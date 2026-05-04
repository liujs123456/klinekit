package com.klinekit.strategy.perp;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Order;
import com.klinekit.domain.Position;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bidirectional grid strategy on a perpetual swap.
 *
 * <p>Sets up evenly-spaced price levels between {@code lowerBound} and
 * {@code upperBound}. The strategy holds a long position; every time price
 * crosses a level downward, it adds to the long ("buy the dip"); every time
 * price crosses upward through a level above the entry, it closes a slice
 * to take profit.
 *
 * <p>Sized so that filling every grid level uses ~{@code initialCash * leverage}
 * of notional total, capping how aggressive the system gets.
 */
public final class Grid implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String symbol;
    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;
    private final int levels;
    private final BigDecimal leverage;
    private final BigDecimal qtyPerLevel;
    private final boolean autoInject;

    private BigDecimal[] gridPrices;
    private boolean[] longActive;
    private BigDecimal lastPrice;

    public Grid(String symbol, BigDecimal lowerBound, BigDecimal upperBound, int levels,
                 BigDecimal leverage, BigDecimal qtyPerLevel) {
        this(symbol, lowerBound, upperBound, levels, leverage, qtyPerLevel, true);
    }

    public Grid(String symbol, BigDecimal lowerBound, BigDecimal upperBound, int levels,
                 BigDecimal leverage, BigDecimal qtyPerLevel, boolean autoInject) {
        if (lowerBound.compareTo(upperBound) >= 0) {
            throw new IllegalArgumentException("lowerBound must be < upperBound");
        }
        if (levels < 2) throw new IllegalArgumentException("levels must be >= 2");
        if (leverage.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("perp.Grid requires leverage > 1");
        }
        this.symbol = symbol;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.levels = levels;
        this.leverage = leverage;
        this.qtyPerLevel = qtyPerLevel;
        this.autoInject = autoInject;
    }

    @Override
    public String name() { return "perp.grid"; }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("lowerBound", lowerBound.toPlainString());
        m.put("upperBound", upperBound.toPlainString());
        m.put("levels", levels);
        m.put("leverage", leverage.toPlainString());
        m.put("qtyPerLevel", qtyPerLevel.toPlainString());
        m.put("autoInject", autoInject);
        return Map.copyOf(m);
    }

    @Override
    public void onStart(StrategyContext ctx) {
        gridPrices = new BigDecimal[levels];
        longActive = new boolean[levels];
        BigDecimal step = upperBound.subtract(lowerBound).divide(BigDecimal.valueOf(levels - 1), MC);
        for (int i = 0; i < levels; i++) {
            gridPrices[i] = lowerBound.add(step.multiply(BigDecimal.valueOf(i)));
        }
        lastPrice = ctx.candle().close();
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;

        BigDecimal cur = c.close();
        if (lastPrice == null) { lastPrice = cur; return; }

        // Buy when price crosses a level downward (dip-buy)
        for (int i = 0; i < levels; i++) {
            BigDecimal level = gridPrices[i];
            boolean crossedDown = lastPrice.compareTo(level) > 0 && cur.compareTo(level) <= 0;
            if (crossedDown && !longActive[i]) {
                BigDecimal margin = cur.multiply(qtyPerLevel).divide(leverage, MC);
                if (autoInject) ctx.portfolio().injectCash(margin);
                if (!ctx.portfolio().hasCash(margin)) continue;
                ctx.router().submit(Order.openPerp(symbol, Direction.LONG, qtyPerLevel, leverage, c.timestamp()));
                longActive[i] = true;
            }
        }

        // Take profit when price crosses upward through a level above any active entry
        Position p = ctx.portfolio().position(symbol);
        if (!p.isFlat() && p.direction() == Direction.LONG) {
            for (int i = levels - 1; i >= 0; i--) {
                BigDecimal level = gridPrices[i];
                boolean crossedUp = lastPrice.compareTo(level) < 0 && cur.compareTo(level) >= 0;
                if (crossedUp && longActive[i] && level.compareTo(p.avgCost()) > 0) {
                    BigDecimal closeQty = qtyPerLevel.min(p.quantity());
                    if (closeQty.signum() > 0) {
                        ctx.router().submit(Order.closePerp(symbol, Direction.LONG, closeQty, leverage, c.timestamp()));
                        longActive[i] = false;
                    }
                }
            }
        }

        lastPrice = cur;
    }

    @SuppressWarnings("unused")
    private Map<String, BigDecimal> trace() { return new HashMap<>(); }  // reserved for diagnostics
}
