package com.klinekit.api.service;

import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.spot.Dca;
import com.klinekit.strategy.spot.DipLadder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Component
public class StrategyFactory {

    public Strategy build(String name, String symbol, Map<String, Object> params) {
        Map<String, Object> p = params == null ? Map.of() : params;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "dca", "spot.dca" -> new Dca(
                    symbol,
                    bd(p, "usdPerBuy", "100"),
                    intOf(p, "intervalDays", 7));
            case "dip-ladder", "spot.dip-ladder" -> new DipLadder(
                    symbol,
                    DipLadder.DEFAULT_TIERS,
                    intOf(p, "refLookbackDays", 30));
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }

    private static BigDecimal bd(Map<String, Object> p, String k, String defaultValue) {
        Object v = p.get(k);
        if (v == null) return new BigDecimal(defaultValue);
        return new BigDecimal(v.toString());
    }

    private static int intOf(Map<String, Object> p, String k, int defaultValue) {
        Object v = p.get(k);
        if (v == null) return defaultValue;
        return Integer.parseInt(v.toString());
    }
}
