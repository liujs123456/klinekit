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
        List<CandleDto> candles,
        DataSourceSpec source
) {
    public record DataSourceSpec(
            String provider,   // "okx"
            String symbol,     // e.g. "BTC-USDT" — falls back to outer symbol if null
            String bar,        // "1D" / "1H" etc — defaults to "1D" if null
            Integer count      // number of candles — defaults to 365 if null
    ) {}
}
