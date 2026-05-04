package com.klinekit.strategy.spot;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Order;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dollar-cost-averaging spot strategy: buy {@code usdPerBuy} of {@code symbol}
 * every {@code intervalDays} days.
 *
 * <p>Two cash modes:
 * <ul>
 *   <li><b>autoInject = true (default)</b> — the strategy treats each buy moment
 *       as a fresh deposit from outside the account ("paycheck → buy BTC").
 *       The portfolio is topped up by {@code usdPerBuy} just before the order,
 *       and {@code Portfolio.totalInjected()} accumulates how much real-world
 *       capital was deployed. This is the realistic DCA model.</li>
 *   <li><b>autoInject = false</b> — the strategy draws from the portfolio's
 *       initial cash and stops when it runs out. Useful for "I have $X to put
 *       to work, drip it in over time" scenarios.</li>
 * </ul>
 */
public final class Dca implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String symbol;
    private final BigDecimal usdPerBuy;
    private final int intervalDays;
    private final boolean autoInject;

    private Instant nextBuyAt;

    public Dca(String symbol, BigDecimal usdPerBuy, int intervalDays) {
        this(symbol, usdPerBuy, intervalDays, true);
    }

    public Dca(String symbol, BigDecimal usdPerBuy, int intervalDays, boolean autoInject) {
        if (intervalDays <= 0) throw new IllegalArgumentException("intervalDays must be positive");
        if (usdPerBuy.signum() <= 0) throw new IllegalArgumentException("usdPerBuy must be positive");
        this.symbol = symbol;
        this.usdPerBuy = usdPerBuy;
        this.intervalDays = intervalDays;
        this.autoInject = autoInject;
    }

    @Override
    public String name() {
        return "spot.dca";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("usdPerBuy", usdPerBuy.toPlainString());
        m.put("intervalDays", intervalDays);
        m.put("autoInject", autoInject);
        return Map.copyOf(m);
    }

    @Override
    public void onStart(StrategyContext ctx) {
        nextBuyAt = ctx.candle().timestamp();
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;
        if (nextBuyAt == null) nextBuyAt = c.timestamp();
        if (c.timestamp().isBefore(nextBuyAt)) return;

        BigDecimal price = c.close();
        if (price.signum() <= 0) return;

        if (autoInject) {
            ctx.portfolio().injectCash(usdPerBuy);
        }

        if (!ctx.portfolio().hasCash(usdPerBuy)) return;

        BigDecimal qty = usdPerBuy.divide(price, MC);
        ctx.router().submit(Order.marketBuy(symbol, qty, c.timestamp()));
        nextBuyAt = c.timestamp().plus(Duration.ofDays(intervalDays));
    }
}
