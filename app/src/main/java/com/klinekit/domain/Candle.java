package com.klinekit.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        String symbol,
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
    public Candle {
        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high < low for " + symbol + " @ " + timestamp);
        }
    }
}
