package com.klinekit.strategy.perp;

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

class GridTest {

    private static final BigDecimal NO_FEE = BigDecimal.ZERO;

    @Test
    void buysOnDownwardCrossesAndCanTakeProfitOnUpward() {
        // Grid 90→110 with 5 levels: 90, 95, 100, 105, 110
        // Path: start 102 (no level crossed), drop to 96 (crosses 100), drop to 91 (crosses 95),
        // bounce to 102 (crosses 95 up but level <= avgCost → no TP; crosses 100 up → TP one slice
        // since avgCost ≈ (100+95)/2 = 97.5 < 100).
        var strat = new Grid("BTC-USDT",
                new BigDecimal("90"), new BigDecimal("110"), 5,
                new BigDecimal("5"), new BigDecimal("0.5"));
        BacktestResult r = run(strat, candles(new double[]{102, 96, 91, 102}));

        assertThat(r.trades()).isNotEmpty();
        long buys = r.trades().stream().filter(t -> t.side().name().equals("BUY")).count();
        long sells = r.trades().stream().filter(t -> t.side().name().equals("SELL")).count();
        assertThat(buys).isGreaterThanOrEqualTo(2);
        assertThat(sells).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsBadBounds() {
        try {
            new Grid("BTC", new BigDecimal("100"), new BigDecimal("100"), 5,
                    new BigDecimal("5"), new BigDecimal("0.5"));
            throw new AssertionError("expected IAE");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static BacktestResult run(Grid g, List<Candle> candles) {
        return new BacktestEngine(new BacktestEngine.Config(
                new BigDecimal("100000"), NO_FEE, NO_FEE)).run(g, candles);
    }

    private static List<Candle> candles(double[] closes) {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal p = BigDecimal.valueOf(closes[i]);
            out.add(new Candle("BTC-USDT", t.plus(i, ChronoUnit.HOURS),
                    p, p, p, p, BigDecimal.ZERO));
        }
        return out;
    }
}
