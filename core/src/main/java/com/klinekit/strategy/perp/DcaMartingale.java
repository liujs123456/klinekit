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
 * <p>Opens a position in the configured direction, then doubles down each time
 * price moves against the position by {@code pullbackPct}. Closes the entire
 * position when price recovers by {@code takeProfitPct} above (LONG) or below
 * (SHORT) the running average entry. Liquidation is handled by the engine via
 * {@link com.klinekit.engine.LiquidationCalculator}.
 *
 * <p><b>Risk controls (off by default):</b>
 * <ul>
 *   <li>{@code stopLossPct} — close the entire position when total unrealised PnL
 *       drops to {@code -stopLossPct * margin posted}. Sized in margin terms so a
 *       50% stop with 5x leverage triggers at ~10% adverse price move on the
 *       initial entry, which is more conservative than letting it ride to liq.
 *   <li>{@code trailingStopPct} — once the position is in profit, lock in the
 *       running peak; if mark price retraces by {@code trailingStopPct} from the
 *       peak (signed by direction), close the whole position.
 * </ul>
 *
 * <p>Defaults are inspired by OKX's published DCA-Martingale 合约马丁 templates:
 * 5x leverage, 2% pullback, 1% take-profit above the running average entry,
 * doubling base size on each refill, no stop-loss / trailing stop unless explicitly
 * enabled.
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
    private final BigDecimal stopLossPct;     // 0 = disabled; else fraction of posted margin
    private final BigDecimal trailingStopPct; // 0 = disabled; else retracement from peak in price terms

    private int filledOrders;
    private BigDecimal lastFillPrice;
    private BigDecimal nextFillSize;
    private BigDecimal peakPrice;

    public DcaMartingale(String symbol, Direction direction, BigDecimal leverage,
                         BigDecimal baseQty, BigDecimal pullbackPct,
                         BigDecimal takeProfitPct, BigDecimal multiplier, int maxOrders) {
        this(symbol, direction, leverage, baseQty, pullbackPct, takeProfitPct,
                multiplier, maxOrders, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public DcaMartingale(String symbol, Direction direction, BigDecimal leverage,
                         BigDecimal baseQty, BigDecimal pullbackPct,
                         BigDecimal takeProfitPct, BigDecimal multiplier, int maxOrders,
                         BigDecimal stopLossPct, BigDecimal trailingStopPct) {
        if (leverage.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("perp.DcaMartingale requires leverage > 1");
        }
        if (pullbackPct.signum() <= 0) throw new IllegalArgumentException("pullbackPct must be > 0");
        if (takeProfitPct.signum() <= 0) throw new IllegalArgumentException("takeProfitPct must be > 0");
        if (maxOrders < 1) throw new IllegalArgumentException("maxOrders must be >= 1");
        if (stopLossPct == null) stopLossPct = BigDecimal.ZERO;
        if (trailingStopPct == null) trailingStopPct = BigDecimal.ZERO;
        this.symbol = symbol;
        this.direction = direction;
        this.leverage = leverage;
        this.baseQty = baseQty;
        this.pullbackPct = pullbackPct;
        this.takeProfitPct = takeProfitPct;
        this.multiplier = multiplier;
        this.maxOrders = maxOrders;
        this.stopLossPct = stopLossPct;
        this.trailingStopPct = trailingStopPct;
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
        m.put("stopLossPct", stopLossPct.toPlainString());
        m.put("trailingStopPct", trailingStopPct.toPlainString());
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
                filledOrders = 0;
                lastFillPrice = null;
                nextFillSize = null;
                peakPrice = null;
            }
            BigDecimal margin = price.multiply(baseQty).divide(leverage, MC);
            if (!ctx.portfolio().hasCash(margin)) return;
            ctx.router().submit(Order.openPerp(symbol, direction, baseQty, leverage, c.timestamp()));
            filledOrders = 1;
            lastFillPrice = price;
            nextFillSize = baseQty.multiply(multiplier);
            peakPrice = price;
            return;
        }

        // Update peak (favourable extreme since opening)
        if (peakPrice == null) peakPrice = price;
        if (direction == Direction.LONG && price.compareTo(peakPrice) > 0) peakPrice = price;
        if (direction == Direction.SHORT && price.compareTo(peakPrice) < 0) peakPrice = price;

        // Trailing stop — only after position is in profit relative to avgCost
        if (trailingStopPct.signum() > 0) {
            boolean inProfit = direction == Direction.LONG
                    ? peakPrice.compareTo(pos.avgCost()) > 0
                    : peakPrice.compareTo(pos.avgCost()) < 0;
            if (inProfit) {
                BigDecimal stopPx = direction == Direction.LONG
                        ? peakPrice.multiply(BigDecimal.ONE.subtract(trailingStopPct))
                        : peakPrice.multiply(BigDecimal.ONE.add(trailingStopPct));
                boolean trailed = direction == Direction.LONG
                        ? price.compareTo(stopPx) <= 0
                        : price.compareTo(stopPx) >= 0;
                if (trailed && stopPx.compareTo(pos.avgCost()) != 0) {
                    closeAll(ctx, pos, c);
                    return;
                }
            }
        }

        // Hard stop-loss — close when uPnL drops to -stopLossPct * posted margin.
        if (stopLossPct.signum() > 0) {
            BigDecimal margin = pos.avgCost().multiply(pos.quantity()).divide(leverage, MC);
            BigDecimal threshold = margin.multiply(stopLossPct).negate();
            BigDecimal upnl = pos.unrealizedPnl(price);
            if (upnl.compareTo(threshold) <= 0) {
                closeAll(ctx, pos, c);
                return;
            }
        }

        // Take profit?
        BigDecimal tpPrice = direction == Direction.LONG
                ? pos.avgCost().multiply(BigDecimal.ONE.add(takeProfitPct))
                : pos.avgCost().multiply(BigDecimal.ONE.subtract(takeProfitPct));
        boolean tpHit = direction == Direction.LONG ? price.compareTo(tpPrice) >= 0 : price.compareTo(tpPrice) <= 0;
        if (tpHit) {
            closeAll(ctx, pos, c);
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

    private void closeAll(StrategyContext ctx, Position pos, Candle c) {
        ctx.router().submit(Order.closePerp(symbol, direction, pos.quantity(), leverage, c.timestamp()));
        filledOrders = 0;
        lastFillPrice = null;
        nextFillSize = null;
        peakPrice = null;
    }
}
