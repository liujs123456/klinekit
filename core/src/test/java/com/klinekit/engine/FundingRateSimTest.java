package com.klinekit.engine;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FundingRateSimTest {

    @Test
    void longPaysWhenRatePositive() {
        Instant t0 = Instant.parse("2024-01-01T07:00:00Z");
        Position p = Position.emptyPerp("BTC-USDT", Direction.LONG, BigDecimal.TEN)
                .applyOpen(BigDecimal.ONE, new BigDecimal("100000"));
        FundingRateSim sim = new FundingRateSim(new BigDecimal("0.0001"));

        // Prime with first candle at 07:00 UTC — next funding rounds up to 08:00
        sim.onCandle(synthetic(t0));
        // Move forward to 09:00 — exactly one funding period crossed
        BigDecimal cash = sim.accrue(p, new BigDecimal("100000"), t0.plus(2, ChronoUnit.HOURS));
        // notional = 100000, fee = 100000 * 0.0001 = 10, long pays → -10
        assertThat(cash).isCloseTo(new BigDecimal("-10"), within(new BigDecimal("0.0001")));
    }

    @Test
    void shortReceivesAcrossMultiplePeriods() {
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Position p = Position.emptyPerp("BTC-USDT", Direction.SHORT, BigDecimal.TEN)
                .applyOpen(BigDecimal.ONE, new BigDecimal("100000"));
        FundingRateSim sim = new FundingRateSim(new BigDecimal("0.0001"));
        sim.onCandle(synthetic(t0));
        // 25 hours -> spans 3 funding moments (08, 16, 24)
        BigDecimal cash = sim.accrue(p, new BigDecimal("100000"), t0.plus(25, ChronoUnit.HOURS));
        // 3 * 10 = 30 received
        assertThat(cash).isCloseTo(new BigDecimal("30"), within(new BigDecimal("0.0001")));
    }

    @Test
    void spotPositionsAreUntouched() {
        Instant t0 = Instant.parse("2024-01-01T07:00:00Z");
        Position spot = Position.empty("BTC-USDT").applyOpen(BigDecimal.ONE, new BigDecimal("100"));
        FundingRateSim sim = new FundingRateSim(new BigDecimal("0.0001"));
        sim.onCandle(synthetic(t0));
        BigDecimal cash = sim.accrue(spot, new BigDecimal("100"), t0.plus(2, ChronoUnit.HOURS));
        assertThat(cash.signum()).isZero();
    }

    private static Candle synthetic(Instant t) {
        BigDecimal v = BigDecimal.valueOf(100);
        return new Candle("BTC-USDT", t, v, v, v, v, BigDecimal.ZERO);
    }
}
