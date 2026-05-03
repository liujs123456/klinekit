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

class DcaTest {

    @Test
    void buysOnceEveryIntervalDays() {
        List<Candle> candles = flatCandles("BTC", 30, new BigDecimal("100"));
        Dca strat = new Dca("BTC", new BigDecimal("100"), 7);

        BacktestResult r = new BacktestEngine(
                new BacktestEngine.Config(new BigDecimal("10000"),
                        BigDecimal.ZERO, BigDecimal.ZERO)).run(strat, candles);

        // 30 days / 7 = buys at day 0, 7, 14, 21, 28 -> 5 trades
        assertThat(r.trades()).hasSize(5);
        assertThat(r.trades()).allSatisfy(t ->
                assertThat(t.side()).isEqualTo(com.klinekit.domain.Side.BUY));
    }

    @Test
    void zeroFeeFlatPriceProducesZeroPnl() {
        List<Candle> candles = flatCandles("BTC", 30, new BigDecimal("100"));
        Dca strat = new Dca("BTC", new BigDecimal("100"), 7);
        BacktestResult r = new BacktestEngine(
                new BacktestEngine.Config(new BigDecimal("10000"),
                        BigDecimal.ZERO, BigDecimal.ZERO)).run(strat, candles);

        // bought @ 100, market still @ 100 -> equity should equal initial cash
        assertThat(r.finalEquity()).isEqualByComparingTo("10000");
    }

    @Test
    void priceUpProducesPositiveReturn() {
        List<Candle> candles = new ArrayList<>();
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 30; i++) {
            BigDecimal px = new BigDecimal(100 + i); // 100 → 129
            candles.add(new Candle("BTC", t.plus(i, ChronoUnit.DAYS),
                    px, px, px, px, BigDecimal.ZERO));
        }
        Dca strat = new Dca("BTC", new BigDecimal("100"), 7);
        BacktestResult r = new BacktestEngine(
                new BacktestEngine.Config(new BigDecimal("10000"),
                        BigDecimal.ZERO, BigDecimal.ZERO)).run(strat, candles);

        assertThat(r.finalEquity()).isGreaterThan(new BigDecimal("10000"));
        assertThat(r.metrics().get("totalReturnPct")).isPositive();
    }

    @Test
    void stopsBuyingWhenCashExhausted() {
        List<Candle> candles = flatCandles("BTC", 365, new BigDecimal("100"));
        Dca strat = new Dca("BTC", new BigDecimal("500"), 1); // $500/day for a year > $10k
        BacktestResult r = new BacktestEngine(
                new BacktestEngine.Config(new BigDecimal("10000"),
                        BigDecimal.ZERO, BigDecimal.ZERO)).run(strat, candles);

        // 10000 / 500 = exactly 20 trades, then cash exhausted
        assertThat(r.trades()).hasSize(20);
    }

    private static List<Candle> flatCandles(String symbol, int days, BigDecimal price) {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            out.add(new Candle(symbol, t.plus(i, ChronoUnit.DAYS),
                    price, price, price, price, BigDecimal.ZERO));
        }
        return out;
    }
}
