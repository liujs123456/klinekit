package com.klinekit.strategy.perp;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Order;
import com.klinekit.domain.Position;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Perpetual contract DCA-Martingale — a popular OKX retail strategy.
 *
 * <p>Opens a long position, then doubles down each time price drops by a
 * configured pullback percentage. Closes the entire position when price
 * recovers to a target above the (averaged-down) entry. Liquidation
 * handled separately by the engine via {@link com.klinekit.engine.LiquidationCalculator}.
 *
 * <p>Defaults are inspired by OKX's published DCA-Martingale 合约马丁 templates:
 * 5x leverage, 2% pullback, 1% take-profit above the running average entry,
 * doubling base size on each refill.
 */
public final class DcaMartingale implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String symbol;
    private final Direction direction;
    private final BigDecimal leverage;
    private final BigDecimal baseQty;
    private final BigDecimal pullbackPct;     // e.g. 0.02 for 2%
    private final BigDecimal takeProfitPct;   // e.g. 0.01 for 1%
    private final BigDecimal multiplier;      // e.g. 2 for double down
    private final int maxOrders;

    private int filledOrders;
    private BigDecimal lastFillPrice;
    private BigDecimal nextFillSize;

    public DcaMartingale(String symbol, Direction direction, BigDecimal leverage,
                         BigDecimal baseQty, BigDecimal pullbackPct,
                         BigDecimal takeProfitPct, BigDecimal multiplier, int maxOrders) {
        if (leverage.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("perp.DcaMartingale requires leverage > 1");
        }
        if (pullbackPct.signum() <= 0) throw new IllegalArgumentException("pullbackPct must be > 0");
        if (takeProfitPct.signum() <= 0) throw new IllegalArgumentException("takeProfitPct must be > 0");
        if (maxOrders < 1) throw new IllegalArgumentException("maxOrders must be >= 1");
        this.symbol = symbol;
        this.direction = direction;
        this.leverage = leverage;
        this.baseQty = baseQty;
        this.pullbackPct = pullbackPct;
        this.takeProfitPct = takeProfitPct;
        this.multiplier = multiplier;
        this.maxOrders = maxOrders;
    }

    @Override
    public String name() { return "perp.dca-martingale"; }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("direction", direction.name());
        m.put("leverage", leverage.toPlainString());
        m.put("baseQty", baseQty.toPlainString());
        m.put("pullbackPct", pullbackPct.toPlainString());
        m.put("takeProfitPct", takeProfitPct.toPlainString());
        m.put("multiplier", multiplier.toPlainString());
        m.put("maxOrders", maxOrders);
        return Map.copyOf(m);
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;
        BigDecimal price = c.close();
        Position pos = ctx.portfolio().position(symbol);

        // Initial entry
        if (pos.isFlat()) {
            if (filledOrders >= maxOrders) {
                // Cycle complete — reset for the next round.
                filledOrders = 0;
                lastFillPrice = null;
                nextFillSize = null;
            }
            BigDecimal margin = price.multiply(baseQty).divide(leverage, MC);
            if (!ctx.portfolio().hasCash(margin)) return;
            ctx.router().submit(Order.openPerp(symbol, direction, baseQty, leverage, c.timestamp()));
            filledOrders = 1;
            lastFillPrice = price;
            nextFillSize = baseQty.multiply(multiplier);
            return;
        }

        // Take profit?
        BigDecimal tpPrice = direction == Direction.LONG
                ? pos.avgCost().multiply(BigDecimal.ONE.add(takeProfitPct))
                : pos.avgCost().multiply(BigDecimal.ONE.subtract(takeProfitPct));
        boolean tpHit = direction == Direction.LONG ? price.compareTo(tpPrice) >= 0 : price.compareTo(tpPrice) <= 0;
        if (tpHit) {
            ctx.router().submit(Order.closePerp(symbol, direction, pos.quantity(), leverage, c.timestamp()));
            filledOrders = 0;
            lastFillPrice = null;
            nextFillSize = null;
            return;
        }

        // Add a martingale step?
        if (filledOrders >= maxOrders || lastFillPrice == null || nextFillSize == null) return;
        BigDecimal trigger = direction == Direction.LONG
                ? lastFillPrice.multiply(BigDecimal.ONE.subtract(pullbackPct))
                : lastFillPrice.multiply(BigDecimal.ONE.add(pullbackPct));
        boolean refillHit = direction == Direction.LONG ? price.compareTo(trigger) <= 0 : price.compareTo(trigger) >= 0;
        if (refillHit) {
            BigDecimal margin = price.multiply(nextFillSize).divide(leverage, MC);
            if (!ctx.portfolio().hasCash(margin)) return;
            ctx.router().submit(Order.openPerp(symbol, direction, nextFillSize, leverage, c.timestamp()));
            filledOrders++;
            lastFillPrice = price;
            nextFillSize = nextFillSize.multiply(multiplier);
        }
    }
}
