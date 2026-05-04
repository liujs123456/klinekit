package com.klinekit.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPointDto(int seq, Instant timestamp, BigDecimal equity, String kind) {
    public EquityPointDto(int seq, Instant timestamp, BigDecimal equity) {
        this(seq, timestamp, equity, "STRATEGY");
    }
}
