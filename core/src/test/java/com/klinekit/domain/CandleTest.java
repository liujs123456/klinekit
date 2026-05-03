package com.klinekit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleTest {

    @Test
    void rejectsHighBelowLow() {
        assertThatThrownBy(() -> new Candle(
                "BTCUSDT", Instant.EPOCH,
                new BigDecimal("100"), new BigDecimal("90"),
                new BigDecimal("95"), new BigDecimal("98"),
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
