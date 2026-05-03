package com.klinekit.metrics;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record MetricSet(
        BigDecimal totalReturnPct,
        BigDecimal sharpe,
        BigDecimal maxDrawdownPct,
        BigDecimal winRatePct,
        int tradeCount
) {
    public Map<String, BigDecimal> asMap() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("totalReturnPct", totalReturnPct);
        m.put("sharpe", sharpe);
        m.put("maxDrawdownPct", maxDrawdownPct);
        m.put("winRatePct", winRatePct);
        m.put("tradeCount", BigDecimal.valueOf(tradeCount));
        return Map.copyOf(m);
    }
}
