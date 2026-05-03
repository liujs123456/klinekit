package com.klinekit.metrics;

import com.klinekit.domain.EquityPoint;
import com.klinekit.domain.Side;
import com.klinekit.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTest {

    @Test
    void totalReturnPctMatchesObservedDelta() {
        List<EquityPoint> curve = List.of(
                new EquityPoint(Instant.EPOCH, new BigDecimal("1000")),
                new EquityPoint(Instant.EPOCH.plusSeconds(86_400), new BigDecimal("1100"))
        );
        BigDecimal pct = Metrics.totalReturnPct(curve, new BigDecimal("1000"));
        assertThat(pct).isEqualByComparingTo("10.0000");
    }

    @Test
    void maxDrawdownDetectsDeepestTrough() {
        Instant t = Instant.EPOCH;
        List<EquityPoint> curve = List.of(
                new EquityPoint(t, new BigDecimal("100")),
                new EquityPoint(t.plusSeconds(1), new BigDecimal("120")),
                new EquityPoint(t.plusSeconds(2), new BigDecimal("60")), // 50% dd from peak 120
                new EquityPoint(t.plusSeconds(3), new BigDecimal("90")),
                new EquityPoint(t.plusSeconds(4), new BigDecimal("110"))
        );
        BigDecimal dd = Metrics.maxDrawdownPct(curve);
        assertThat(dd).isEqualByComparingTo("50.0000");
    }

    @Test
    void flatCurveHasZeroSharpe() {
        Instant t = Instant.EPOCH;
        List<EquityPoint> curve = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            curve.add(new EquityPoint(t.plus(i, ChronoUnit.DAYS), new BigDecimal("100")));
        }
        assertThat(Metrics.sharpe(curve)).isEqualByComparingTo("0");
    }

    @Test
    void winRateZeroWithoutSells() {
        List<Trade> trades = List.of(
                new Trade("a", "BTC", Side.BUY, BigDecimal.ONE, new BigDecimal("100"),
                        BigDecimal.ZERO, Instant.EPOCH)
        );
        assertThat(Metrics.winRatePct(trades)).isEqualByComparingTo("0");
    }

    @Test
    void winRateCountsProfitableSells() {
        List<Trade> trades = List.of(
                new Trade("1", "BTC", Side.BUY, BigDecimal.ONE, new BigDecimal("100"),
                        BigDecimal.ZERO, Instant.EPOCH),
                new Trade("2", "BTC", Side.SELL, BigDecimal.ONE, new BigDecimal("150"),
                        BigDecimal.ZERO, Instant.EPOCH), // win
                new Trade("3", "BTC", Side.BUY, BigDecimal.ONE, new BigDecimal("200"),
                        BigDecimal.ZERO, Instant.EPOCH),
                new Trade("4", "BTC", Side.SELL, BigDecimal.ONE, new BigDecimal("180"),
                        BigDecimal.ZERO, Instant.EPOCH)  // loss
        );
        assertThat(Metrics.winRatePct(trades)).isEqualByComparingTo("50.0000");
    }
}
