package com.klinekit.api.service;

import com.klinekit.domain.Direction;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.perp.DcaMartingale;
import com.klinekit.strategy.perp.Grid;
import com.klinekit.strategy.spot.Dca;
import com.klinekit.strategy.spot.Dca.CashMode;
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
            case "dca", "spot.dca" -> {
                CashMode mode = parseCashMode(p);
                int interval = intOf(p, "intervalDays", 7);
                if (mode == CashMode.PHASED) {
                    yield new Dca(symbol, BigDecimal.ZERO,
                            bd(p, "phasedBudget", "10000"),
                            interval, CashMode.PHASED);
                }
                yield new Dca(symbol,
                        bd(p, "usdPerBuy", "100"),
                        BigDecimal.ZERO, interval, mode);
            }
            case "dip-ladder", "spot.dip-ladder" -> new DipLadder(
                    symbol,
                    DipLadder.DEFAULT_TIERS,
                    intOf(p, "refLookbackDays", 30),
                    boolOf(p, "autoInject", true));
            case "grid", "perp.grid" -> new Grid(
                    symbol,
                    bd(p, "lowerBound", "70000"),
                    bd(p, "upperBound", "100000"),
                    intOf(p, "levels", 8),
                    bd(p, "leverage", "5"),
                    bd(p, "qtyPerLevel", "0.01"),
                    boolOf(p, "autoInject", true));
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

    private static boolean boolOf(Map<String, Object> p, String k, boolean defaultValue) {
        Object v = p.get(k);
        if (v == null) return defaultValue;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    /**
     * Accepts either an explicit {@code cashMode: "AUTO_INJECT"|"PHASED"|"LUMP"}
     * or the legacy boolean {@code autoInject}. Defaults to AUTO_INJECT.
     */
    private static CashMode parseCashMode(Map<String, Object> p) {
        Object explicit = p.get("cashMode");
        if (explicit != null) {
            String raw = explicit.toString().trim().toUpperCase(Locale.ROOT);
            switch (raw) {
                case "AUTO_INJECT", "AUTOINJECT", "AUTO-INJECT" -> { return CashMode.AUTO_INJECT; }
                case "PHASED", "PHASED_ENTRY", "PHASE" -> { return CashMode.PHASED; }
                case "LUMP", "DRAIN", "DRAIN_INITIAL" -> { return CashMode.LUMP; }
                default -> { /* fall through */ }
            }
        }
        if (p.containsKey("autoInject")) {
            return boolOf(p, "autoInject", true) ? CashMode.AUTO_INJECT : CashMode.LUMP;
        }
        return CashMode.AUTO_INJECT;
    }
}
