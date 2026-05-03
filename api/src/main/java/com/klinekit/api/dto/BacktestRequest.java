package com.klinekit.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BacktestRequest(
        String strategy,
        String symbol,
        BigDecimal initialCash,
        BigDecimal feeBps,
        BigDecimal slippageBps,
        Map<String, Object> params,
        List<CandleDto> candles
) {}
