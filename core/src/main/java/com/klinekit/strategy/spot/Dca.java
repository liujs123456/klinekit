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
 * Dollar-cost-averaging spot strategy: buy {@code symbol} every
 * {@code intervalDays} days. The amount per buy depends on the cash mode.
 *
 * <h2>Cash modes</h2>
 * <ul>
 *   <li><b>{@link CashMode#AUTO_INJECT AUTO_INJECT}</b> (default) — recurring
 *       deposit from outside the account ("paycheck → buy BTC"). Buys
 *       {@code usdPerBuy} every interval; the portfolio is topped up just
 *       before each order so initial cash is irrelevant. {@code totalInjected}
 *       reports total real-world capital deployed.</li>
 *
 *   <li><b>{@link CashMode#PHASED PHASED}</b> — finite budget spread evenly over
 *       the entire backtest window. The strategy auto-computes
 *       {@code usdPerBuy = totalBudget / numberOfIntervals} once it sees the
 *       full candle range, so a $2,000 budget over 1,000 days at a 1-day
 *       interval becomes ~$2/day. Initial cash is not drawn from; cash is
 *       injected on each buy like {@code AUTO_INJECT} but the per-buy size is
 *       derived from the budget instead of being user-specified.</li>
 *
 *   <li><b>{@link CashMode#LUMP LUMP}</b> — buys {@code usdPerBuy} every interval
 *       drawing from the portfolio's initial cash and stops when the pool
 *       empties. Mostly useful for "what if I had stopped DCAing midway"
 *       scenarios; intentionally NOT the default.</li>
 * </ul>
 */
public final class Dca implements Strategy {

    public enum CashMode {
        AUTO_INJECT,
        PHASED,
        LUMP
    }

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String symbol;
    private final BigDecimal usdPerBuyConfigured;  // user-supplied; ignored for PHASED
    private final BigDecimal phasedBudget;         // total budget for PHASED mode
    private final int intervalDays;
    private final CashMode cashMode;

    private BigDecimal usdPerBuyEffective;
    private Instant nextBuyAt;
    private Instant rangeStart;
    private Instant rangeEnd;

    public Dca(String symbol, BigDecimal usdPerBuy, int intervalDays) {
        this(symbol, usdPerBuy, BigDecimal.ZERO, intervalDays, CashMode.AUTO_INJECT);
    }

    public Dca(String symbol, BigDecimal usdPerBuy, int intervalDays, boolean autoInject) {
        this(symbol, usdPerBuy, BigDecimal.ZERO, intervalDays,
                autoInject ? CashMode.AUTO_INJECT : CashMode.LUMP);
    }

    public Dca(String symbol, BigDecimal usdPerBuy, BigDecimal phasedBudget,
               int intervalDays, CashMode cashMode) {
        if (intervalDays <= 0) throw new IllegalArgumentException("intervalDays must be positive");
        if (cashMode == null) cashMode = CashMode.AUTO_INJECT;
        if (cashMode == CashMode.PHASED) {
            if (phasedBudget == null || phasedBudget.signum() <= 0) {
                throw new IllegalArgumentException("phasedBudget must be > 0 for CashMode.PHASED");
            }
        } else {
            if (usdPerBuy == null || usdPerBuy.signum() <= 0) {
                throw new IllegalArgumentException("usdPerBuy must be > 0 for AUTO_INJECT / LUMP");
            }
        }
        this.symbol = symbol;
        this.usdPerBuyConfigured = usdPerBuy == null ? BigDecimal.ZERO : usdPerBuy;
        this.phasedBudget = phasedBudget == null ? BigDecimal.ZERO : phasedBudget;
        this.intervalDays = intervalDays;
        this.cashMode = cashMode;
    }

    public static Dca phased(String symbol, BigDecimal totalBudget, int intervalDays) {
        return new Dca(symbol, BigDecimal.ZERO, totalBudget, intervalDays, CashMode.PHASED);
    }

    @Override
    public String name() {
        return "spot.dca";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("intervalDays", intervalDays);
        m.put("cashMode", cashMode.name());
        if (cashMode == CashMode.PHASED) {
            m.put("phasedBudget", phasedBudget.toPlainString());
            if (usdPerBuyEffective != null) {
                m.put("derivedUsdPerBuy", usdPerBuyEffective.toPlainString());
            }
        } else {
            m.put("usdPerBuy", usdPerBuyConfigured.toPlainString());
        }
        return Map.copyOf(m);
    }

    @Override
    public void onStart(StrategyContext ctx) {
        rangeStart = ctx.candle().timestamp();
        nextBuyAt = rangeStart;
    }

    /** Engine calls this just before the loop begins so PHASED can size buys. */
    public void primeRange(Instant start, Instant end) {
        this.rangeStart = start;
        this.rangeEnd = end;
        if (cashMode == CashMode.PHASED) {
            long days = Duration.between(start, end).toDays() + 1;
            long buys = Math.max(1, days / intervalDays);
            usdPerBuyEffective = phasedBudget.divide(BigDecimal.valueOf(buys), MC);
        } else {
            usdPerBuyEffective = usdPerBuyConfigured;
        }
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;
        if (usdPerBuyEffective == null) usdPerBuyEffective = usdPerBuyConfigured;
        if (nextBuyAt == null) nextBuyAt = c.timestamp();
        if (c.timestamp().isBefore(nextBuyAt)) return;

        BigDecimal price = c.close();
        if (price.signum() <= 0) return;

        BigDecimal amount = usdPerBuyEffective;
        if (amount.signum() <= 0) return;

        if (cashMode == CashMode.AUTO_INJECT || cashMode == CashMode.PHASED) {
            ctx.portfolio().injectCash(amount);
        }
        if (!ctx.portfolio().hasCash(amount)) return;

        BigDecimal qty = amount.divide(price, MC);
        ctx.router().submit(Order.marketBuy(symbol, qty, c.timestamp()));
        nextBuyAt = c.timestamp().plus(Duration.ofDays(intervalDays));
    }
}
