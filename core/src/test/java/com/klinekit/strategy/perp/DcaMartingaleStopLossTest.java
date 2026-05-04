package com.klinekit.strategy.perp;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.domain.Side;
import com.klinekit.engine.BacktestEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DcaMartingaleStopLossTest {

    private static final BigDecimal NO_FEE = BigDecimal.ZERO;

    @Test
    void hardStopLossClosesOnAdverseMove() {
        // 10x leverage, stop at 50% of margin loss = ~5% adverse price move on the open.
        // Open at 100, drop to 94 — uPnL/margin ~ -60%, stop should fire.
        var strat = new DcaMartingale(
                "BTC-USDT", Direction.LONG,
                BigDecimal.TEN,
                BigDecimal.ONE,
                new BigDecimal("0.20"),     // pullback far enough so we don't double down
                new BigDecimal("0.50"),     // tp far away
                new BigDecimal("2"), 5,
                new BigDecimal("0.50"),     // 50% stop loss
                BigDecimal.ZERO);
        BacktestResult r = run(strat, candles(new double[]{100.0, 94.0}));
        assertThat(r.trades()).hasSize(2);
        assertThat(r.trades().get(0).side()).isEqualTo(Side.BUY);  // open LONG
        assertThat(r.trades().get(1).side()).isEqualTo(Side.SELL); // stop-loss close
    }

    @Test
    void trailingStopClosesAfterPeakRetraces() {
        // Open LONG at 100, rally to 110 (peak), then trailing 3% retrace.
        // Trail price = 110 * 0.97 = 106.7. Drop to 106 should fire.
        var strat = new DcaMartingale(
                "BTC-USDT", Direction.LONG,
                BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.20"),
                new BigDecimal("0.50"),     // tp far away (won't trigger)
                new BigDecimal("2"), 5,
                BigDecimal.ZERO,
                new BigDecimal("0.03"));    // 3% trailing
        BacktestResult r = run(strat, candles(new double[]{100.0, 110.0, 106.0}));
        assertThat(r.trades()).hasSize(2);
        assertThat(r.trades().get(1).side()).isEqualTo(Side.SELL);
        // Closed in profit: 110 retrace to 106 still > 100 entry
        assertThat(r.finalEquity()).isGreaterThan(new BigDecimal("100000"));
    }

    @Test
    void noStopWhenNeverInProfit() {
        // Trail enabled but price never went above entry — must NOT close
        var strat = new DcaMartingale(
                "BTC-USDT", Direction.LONG,
                BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.20"),
                new BigDecimal("0.50"), new BigDecimal("2"), 5,
                BigDecimal.ZERO, new BigDecimal("0.03"));
        BacktestResult r = run(strat, candles(new double[]{100.0, 99.0, 99.5}));
        // Only the initial open
        assertThat(r.trades()).hasSize(1);
        assertThat(r.trades().get(0).side()).isEqualTo(Side.BUY);
    }

    private static BacktestResult run(DcaMartingale s, List<Candle> candles) {
        return new BacktestEngine(new BacktestEngine.Config(
                new BigDecimal("100000"), NO_FEE, NO_FEE)).run(s, candles);
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
