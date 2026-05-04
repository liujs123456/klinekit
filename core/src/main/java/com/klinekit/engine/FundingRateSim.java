package com.klinekit.engine;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Position;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Funding rate accrual for perpetual swaps.
 *
 * <p>OKX charges/credits funding every 8 hours (00:00, 08:00, 16:00 UTC). We
 * approximate by accruing a configurable per-period rate every 8h on the
 * notional of any open perp position. Long positions pay when the rate is
 * positive, short positions receive (and vice versa). Spot positions are
 * untouched.
 *
 * <p>For a real OKX history feed you'd plug in actual historical funding
 * rates from the OKX history-funding-rate endpoint; for backtests with
 * synthetic candles we accept a fixed rate as a baseline.
 */
public final class FundingRateSim {

    private static final Duration FUNDING_INTERVAL = Duration.ofHours(8);

    private final BigDecimal fixedRate;  // e.g. 0.0001 = 0.01% per 8h
    private Instant nextFundingAt;

    public FundingRateSim(BigDecimal fixedRate) {
        this.fixedRate = fixedRate;
    }

    public void onCandle(Candle c) {
        if (nextFundingAt == null) {
            nextFundingAt = roundUpTo8h(c.timestamp());
        }
    }

    /**
     * Run any funding payments that are due as of `now` for the given position.
     * Returns the cash delta to apply to the portfolio (negative = paid, positive = received).
     */
    public BigDecimal accrue(Position position, BigDecimal markPrice, Instant now) {
        if (nextFundingAt == null || position.isFlat() || !position.isPerp()) {
            return BigDecimal.ZERO;
        }
        BigDecimal cash = BigDecimal.ZERO;
        while (!now.isBefore(nextFundingAt)) {
            BigDecimal notional = position.quantity().multiply(markPrice);
            BigDecimal payment = notional.multiply(fixedRate);
            // LONG pays when rate > 0; SHORT receives.
            if (position.direction() == Direction.LONG) {
                cash = cash.subtract(payment);
            } else {
                cash = cash.add(payment);
            }
            nextFundingAt = nextFundingAt.plus(FUNDING_INTERVAL);
        }
        return cash;
    }

    private static Instant roundUpTo8h(Instant t) {
        long secs = t.getEpochSecond();
        long bucket = 8 * 3600;
        long rounded = ((secs / bucket) + 1) * bucket;
        return Instant.ofEpochSecond(rounded);
    }
}
