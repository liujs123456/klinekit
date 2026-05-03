package com.klinekit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BacktestResult(
        String strategy,
        String symbol,
        Instant start,
        Instant end,
        BigDecimal initialCash,
        BigDecimal finalEquity,
        List<Trade> trades,
        List<EquityPoint> equityCurve,
        Map<String, BigDecimal> metrics
) {
    public BigDecimal totalReturn() {
        return finalEquity.subtract(initialCash);
    }

    public BigDecimal totalReturnPct() {
        if (initialCash.signum() == 0) return BigDecimal.ZERO;
        return totalReturn().divide(initialCash, java.math.MathContext.DECIMAL64);
    }
}
