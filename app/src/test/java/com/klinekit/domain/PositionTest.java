package com.klinekit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PositionTest {

    @Test
    void buyTwiceComputesWeightedAverageCost() {
        Position p = Position.empty("BTCUSDT")
                .applyBuy(new BigDecimal("1"), new BigDecimal("10000"))
                .applyBuy(new BigDecimal("1"), new BigDecimal("20000"));

        assertThat(p.quantity()).isEqualByComparingTo("2");
        assertThat(p.avgCost()).isEqualByComparingTo("15000");
    }

    @Test
    void sellPreservesAvgCostUntilFlat() {
        Position p = Position.empty("BTCUSDT")
                .applyBuy(new BigDecimal("2"), new BigDecimal("100"));
        Position after = p.applySell(new BigDecimal("1"));
        assertThat(after.quantity()).isEqualByComparingTo("1");
        assertThat(after.avgCost()).isEqualByComparingTo("100");

        Position flat = after.applySell(new BigDecimal("1"));
        assertThat(flat.isFlat()).isTrue();
        assertThat(flat.avgCost()).isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnlReflectsMarkPrice() {
        Position p = Position.empty("BTCUSDT").applyBuy(new BigDecimal("2"), new BigDecimal("100"));
        assertThat(p.unrealizedPnl(new BigDecimal("150"))).isEqualByComparingTo("100");
        assertThat(p.unrealizedPnl(new BigDecimal("80"))).isEqualByComparingTo("-40");
    }
}
