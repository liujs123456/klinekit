package com.klinekit.engine;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Position;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Funding rate accrual for perpetual swaps.
 *
 * <p>OKX charges/credits funding every 8 hours (00:00, 08:00, 16:00 UTC). We
 * accrue at each scheduled funding moment using either:
 *
 * <ul>
 *   <li>A historical funding-rate map keyed by {@code fundingTime} (UTC) — looked
 *       up via {@code floorEntry} so each accrual uses the most recent published
 *       rate at or before the funding moment, OR
 *   <li>A fixed rate fallback if no history is provided.
 * </ul>
 *
 * <p>Long positions pay when the rate is positive; short positions receive
 * (and vice versa). Spot positions are untouched.
 */
public final class FundingRateSim {

    private static final Duration FUNDING_INTERVAL = Duration.ofHours(8);

    private final BigDecimal fixedRate;
    private final NavigableMap<Instant, BigDecimal> history;
    private Instant nextFundingAt;

    public FundingRateSim(BigDecimal fixedRate) {
        this(fixedRate, null);
    }

    public FundingRateSim(BigDecimal fixedRate, NavigableMap<Instant, BigDecimal> history) {
        this.fixedRate = fixedRate == null ? BigDecimal.ZERO : fixedRate;
        this.history = history;
    }

    public void onCandle(Candle c) {
        if (nextFundingAt == null) {
            nextFundingAt = roundUpTo8h(c.timestamp());
        }
    }

    public BigDecimal accrue(Position position, BigDecimal markPrice, Instant now) {
        if (nextFundingAt == null || position.isFlat() || !position.isPerp()) {
            return BigDecimal.ZERO;
        }
        BigDecimal cash = BigDecimal.ZERO;
        while (!now.isBefore(nextFundingAt)) {
            BigDecimal rate = rateAt(nextFundingAt);
            BigDecimal notional = position.quantity().multiply(markPrice);
            BigDecimal payment = notional.multiply(rate);
            if (position.direction() == Direction.LONG) {
                cash = cash.subtract(payment);
            } else {
                cash = cash.add(payment);
            }
            nextFundingAt = nextFundingAt.plus(FUNDING_INTERVAL);
        }
        return cash;
    }

    private BigDecimal rateAt(Instant t) {
        if (history != null && !history.isEmpty()) {
            Map.Entry<Instant, BigDecimal> entry = history.floorEntry(t);
            if (entry != null) return entry.getValue();
        }
        return fixedRate;
    }

    private static Instant roundUpTo8h(Instant t) {
        long secs = t.getEpochSecond();
        long bucket = 8 * 3600;
        long rounded = ((secs / bucket) + 1) * bucket;
        return Instant.ofEpochSecond(rounded);
    }
}
