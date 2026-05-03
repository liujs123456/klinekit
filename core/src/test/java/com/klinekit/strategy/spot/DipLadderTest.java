package com.klinekit.strategy.spot;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.engine.BacktestEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DipLadderTest {

    private static final BigDecimal NO_FEE = BigDecimal.ZERO;

    @Test
    void firesT1OnceWhenPriceDrops10Percent() {
        // Days 0-29: $100 (lookback fills, ref = 100)
        // Day 30: $89 → -11% from ref → T1 fires once
        // Day 31: $88 → still in T1 zone but already disarmed → no fire
        List<Candle> candles = candles("BTC", List.of(
                fillN(30, 100), List.of(89.0, 88.0)
        ));
        DipLadder strat = new DipLadder("BTC");
        BacktestResult r = run(strat, candles);
        assertThat(r.trades()).hasSize(1);
        assertThat(r.trades().getFirst().notional())
                .isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.01")));
    }

    @Test
    void deeperTierFiresEvenWhenShallowerAlreadyDisarmed() {
        // Lookback ref = 100. Drop straight through T1 (-10) into T3 (-22): T3 fires.
        // Then climb back partial, fall again into T4: T4 fires.
        List<Candle> candles = candles("BTC", List.of(
                fillN(30, 100),
                List.of(76.0, 70.0)   // 76 → triggers T3 (78), then 70 → triggers T4 (68)? No, T4 = 68. 70 > 68 so T4 not yet.
        ));
        // Adjust: 76 (T3 fires, $400), then 65 (T4 fires, $800)
        candles = candles("BTC", List.of(fillN(30, 100), List.of(76.0, 65.0)));
        DipLadder strat = new DipLadder("BTC");
        BacktestResult r = run(strat, candles);
        assertThat(r.trades()).hasSize(2);
        assertThat(r.trades().get(0).notional())
                .isCloseTo(new BigDecimal("400"), within(new BigDecimal("0.01")));
        assertThat(r.trades().get(1).notional())
                .isCloseTo(new BigDecimal("800"), within(new BigDecimal("0.01")));
    }

    @Test
    void rearmsTierAfterFivePercentRebound() {
        // ref=100, drop to 89 → T1 fires (disarm), rebound to 95 (>= 90*1.05=94.5 → rearm),
        // drop again to 89 → T1 fires again
        List<Candle> candles = candles("BTC", List.of(
                fillN(30, 100), List.of(89.0, 95.0, 89.0)
        ));
        DipLadder strat = new DipLadder("BTC");
        BacktestResult r = run(strat, candles);
        assertThat(r.trades()).hasSize(2);
    }

    @Test
    void doesNothingWithoutEnoughLookback() {
        List<Candle> candles = candles("BTC", List.of(List.of(100.0, 80.0, 60.0)));
        DipLadder strat = new DipLadder("BTC");
        BacktestResult r = run(strat, candles);
        assertThat(r.trades()).isEmpty();
    }

    private static BacktestResult run(DipLadder s, List<Candle> candles) {
        return new BacktestEngine(new BacktestEngine.Config(
                new BigDecimal("10000"), NO_FEE, NO_FEE)).run(s, candles);
    }

    private static List<Double> fillN(int n, double price) {
        List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(price);
        return out;
    }

    private static List<Candle> candles(String symbol, List<List<Double>> chunks) {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<Candle> out = new ArrayList<>();
        int day = 0;
        for (List<Double> chunk : chunks) {
            for (Double p : chunk) {
                BigDecimal px = BigDecimal.valueOf(p);
                out.add(new Candle(symbol, t.plus(day, ChronoUnit.DAYS),
                        px, px, px, px, BigDecimal.ZERO));
                day++;
            }
        }
        return out;
    }
}
