package com.klinekit.engine;

import com.klinekit.domain.Direction;
import com.klinekit.domain.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LiquidationCalculatorTest {

    @Test
    void longTenXLiqAtAbout91Pct() {
        // 10x long with 0.5% maintenance: liq ≈ entry * (1 - 0.1 + 0.005) = entry * 0.905
        Position p = Position.emptyPerp("BTC-USDT", Direction.LONG, BigDecimal.TEN)
                .applyOpen(BigDecimal.ONE, new BigDecimal("100000"));
        BigDecimal liq = LiquidationCalculator.liquidationPrice(p);
        assertThat(liq).isCloseTo(new BigDecimal("90500"), within(new BigDecimal("0.5")));
    }

    @Test
    void short5xLiqAtAbout118Pct() {
        // 5x short: liq ≈ entry * (1 + 0.2 - 0.005) = entry * 1.195
        Position p = Position.emptyPerp("BTC-USDT", Direction.SHORT, new BigDecimal("5"))
                .applyOpen(BigDecimal.ONE, new BigDecimal("100000"));
        BigDecimal liq = LiquidationCalculator.liquidationPrice(p);
        assertThat(liq).isCloseTo(new BigDecimal("119500"), within(new BigDecimal("0.5")));
    }

    @Test
    void wasLiquidatedTriggersOnLongDownwick() {
        Position p = Position.emptyPerp("BTC-USDT", Direction.LONG, BigDecimal.TEN)
                .applyOpen(BigDecimal.ONE, new BigDecimal("100000"));
        // candle low pierces 90500
        assertThat(LiquidationCalculator.wasLiquidated(p, new BigDecimal("99000"), new BigDecimal("90000"))).isTrue();
        // candle stays above
        assertThat(LiquidationCalculator.wasLiquidated(p, new BigDecimal("99000"), new BigDecimal("95000"))).isFalse();
    }

    @Test
    void rejectsSpotLeverage() {
        Position spot = Position.empty("BTC-USDT").applyOpen(BigDecimal.ONE, new BigDecimal("100"));
        assertThatThrownBy(() -> LiquidationCalculator.liquidationPrice(spot))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
