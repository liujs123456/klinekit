package com.klinekit.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BacktestRunSummaryDto(
        UUID id,
        String strategy,
        String symbol,
        Instant startAt,
        Instant endAt,
        BigDecimal initialCash,
        BigDecimal finalEquity,
        Map<String, Object> config,
        Map<String, Object> metrics,
        Instant createdAt,
        int tradeCount,
        int equityPointCount
) {}
