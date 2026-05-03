package com.klinekit.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeDto(
        int seq,
        String orderId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        Instant executedAt
) {}
