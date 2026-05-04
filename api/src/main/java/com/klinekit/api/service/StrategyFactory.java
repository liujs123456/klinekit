package com.klinekit.api.service;

import com.klinekit.domain.Direction;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.perp.DcaMartingale;
import com.klinekit.strategy.perp.Grid;
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
            case "grid", "perp.grid" -> new Grid(
                    symbol,
                    bd(p, "lowerBound", "70000"),
                    bd(p, "upperBound", "100000"),
                    intOf(p, "levels", 8),
                    bd(p, "leverage", "5"),
                    bd(p, "qtyPerLevel", "0.01"));
            case "dca-martingale", "perp.dca-martingale" -> new DcaMartingale(
                    symbol,
                    Direction.valueOf(strOf(p, "direction", "LONG").toUpperCase(Locale.ROOT)),
                    bd(p, "leverage", "5"),
                    bd(p, "baseQty", "0.01"),
                    bd(p, "pullbackPct", "0.02"),
                    bd(p, "takeProfitPct", "0.01"),
                    bd(p, "multiplier", "2"),
                    intOf(p, "maxOrders", 6),
                    bd(p, "stopLossPct", "0"),
                    bd(p, "trailingStopPct", "0"));
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }

    private static String strOf(Map<String, Object> p, String k, String defaultValue) {
        Object v = p.get(k);
        return v == null ? defaultValue : v.toString();
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
