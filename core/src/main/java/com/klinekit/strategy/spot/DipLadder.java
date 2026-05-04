package com.klinekit.strategy.spot;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Order;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DipLadder implements Strategy {

    public record Tier(String name, BigDecimal triggerFactor, BigDecimal usd) {}

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal REARM_BUFFER = new BigDecimal("0.05");

    public static final List<Tier> DEFAULT_TIERS = List.of(
            new Tier("T1", new BigDecimal("0.90"), new BigDecimal("100")),
            new Tier("T2", new BigDecimal("0.85"), new BigDecimal("200")),
            new Tier("T3", new BigDecimal("0.78"), new BigDecimal("400")),
            new Tier("T4", new BigDecimal("0.68"), new BigDecimal("800"))
    );

    private final String symbol;
    private final List<Tier> tiers;
    private final int refLookbackDays;
    private final boolean autoInject;
    private final Map<String, Boolean> armed = new HashMap<>();
    private final Deque<Candle> window = new ArrayDeque<>();

    public DipLadder(String symbol) {
        this(symbol, DEFAULT_TIERS, 30);
    }

    public DipLadder(String symbol, List<Tier> tiers, int refLookbackDays) {
        this(symbol, tiers, refLookbackDays, true);
    }

    public DipLadder(String symbol, List<Tier> tiers, int refLookbackDays, boolean autoInject) {
        if (tiers.isEmpty()) throw new IllegalArgumentException("tiers cannot be empty");
        if (refLookbackDays <= 0) throw new IllegalArgumentException("refLookbackDays must be positive");
        this.symbol = symbol;
        this.tiers = List.copyOf(tiers);
        this.refLookbackDays = refLookbackDays;
        this.autoInject = autoInject;
        for (Tier t : tiers) armed.put(t.name(), true);
    }

    @Override
    public String name() {
        return "spot.dip-ladder";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("refLookbackDays", refLookbackDays);
        m.put("autoInject", autoInject);
        m.put("tiers", tiers.stream().map(t ->
                Map.of("name", t.name(),
                        "triggerFactor", t.triggerFactor().toPlainString(),
                        "usd", t.usd().toPlainString())).toList());
        return Map.copyOf(m);
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;

        window.addLast(c);
        while (window.size() > refLookbackDays) window.removeFirst();
        if (window.size() < refLookbackDays) return;

        BigDecimal ref = window.stream().map(Candle::high).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal price = c.close();

        // Re-arm tiers whose trigger has rebounded by REARM_BUFFER
        for (Tier t : tiers) {
            if (!armed.getOrDefault(t.name(), true)) {
                BigDecimal triggerPx = ref.multiply(t.triggerFactor());
                BigDecimal rearmPx = triggerPx.multiply(BigDecimal.ONE.add(REARM_BUFFER));
                if (price.compareTo(rearmPx) >= 0) armed.put(t.name(), true);
            }
        }

        // Fire deepest armed tier whose trigger price has been crossed
        for (int i = tiers.size() - 1; i >= 0; i--) {
            Tier t = tiers.get(i);
            BigDecimal triggerPx = ref.multiply(t.triggerFactor());
            if (price.compareTo(triggerPx) <= 0 && armed.getOrDefault(t.name(), true)) {
                if (autoInject) ctx.portfolio().injectCash(t.usd());
                if (!ctx.portfolio().hasCash(t.usd())) return;
                BigDecimal qty = t.usd().divide(price, MC);
                ctx.router().submit(Order.marketBuy(symbol, qty, c.timestamp()));
                armed.put(t.name(), false);
                return;
            }
        }
    }
}
