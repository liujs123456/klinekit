package com.klinekit.strategy.perp;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.domain.Direction;
import com.klinekit.engine.BacktestEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DcaMartingaleTest {

    private static final BigDecimal NO_FEE = BigDecimal.ZERO;

    @Test
    void opensInitialPositionOnFirstCandle() {
        var strat = strategy();
        BacktestResult r = run(strat, candleSeries(new double[]{100.0, 100.0, 100.0}));
        // first candle opens, subsequent flat candles do nothing
        assertThat(r.trades()).hasSize(1);
        assertThat(r.trades().getFirst().side().name()).isEqualTo("BUY");
    }

    @Test
    void doublesDownOnPullback() {
        // baseQty 1.0; pullbackPct 5%; multiplier 2x; price drops 100 -> 94 (below 95 trigger)
        var strat = new DcaMartingale("BTC-USDT", Direction.LONG,
                BigDecimal.TEN,                   // 10x leverage
                BigDecimal.ONE,                    // baseQty
                new BigDecimal("0.05"),            // pullback
                new BigDecimal("0.10"),            // tp far enough away to not trigger
                new BigDecimal("2"),               // multiplier
                5);
        BacktestResult r = run(strat, candleSeries(new double[]{100.0, 94.0}));
        assertThat(r.trades()).hasSize(2);
        // second fill is 2x bigger
        assertThat(r.trades().get(1).quantity()).isEqualByComparingTo("2");
    }

    @Test
    void closesAtTakeProfit() {
        var strat = new DcaMartingale("BTC-USDT", Direction.LONG,
                BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.05"),            // pullback far enough to not double
                new BigDecimal("0.02"),            // 2% take profit
                new BigDecimal("2"), 5);
        BacktestResult r = run(strat, candleSeries(new double[]{100.0, 102.5}));
        // open at 100, TP at 102 — second candle close 102.5 triggers close
        assertThat(r.trades()).hasSize(2);
        assertThat(r.trades().get(1).side().name()).isEqualTo("SELL");  // closing a LONG = SELL fill side
    }

    private static DcaMartingale strategy() {
        return new DcaMartingale("BTC-USDT", Direction.LONG,
                BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("0.05"),
                new BigDecimal("0.50"), // huge tp so we never close
                new BigDecimal("2"), 5);
    }

    private static BacktestResult run(DcaMartingale s, List<Candle> candles) {
        return new BacktestEngine(new BacktestEngine.Config(
                new BigDecimal("100000"), NO_FEE, NO_FEE)).run(s, candles);
    }

    private static List<Candle> candleSeries(double[] closes) {
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
