package com.klinekit.strategy.spot;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Order;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Dca implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String symbol;
    private final BigDecimal usdPerBuy;
    private final int intervalDays;

    private Instant nextBuyAt;

    public Dca(String symbol, BigDecimal usdPerBuy, int intervalDays) {
        if (intervalDays <= 0) throw new IllegalArgumentException("intervalDays must be positive");
        if (usdPerBuy.signum() <= 0) throw new IllegalArgumentException("usdPerBuy must be positive");
        this.symbol = symbol;
        this.usdPerBuy = usdPerBuy;
        this.intervalDays = intervalDays;
    }

    @Override
    public String name() {
        return "spot.dca";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("usdPerBuy", usdPerBuy.toPlainString());
        m.put("intervalDays", intervalDays);
        return Map.copyOf(m);
    }

    @Override
    public void onStart(StrategyContext ctx) {
        nextBuyAt = ctx.candle().timestamp();
    }

    @Override
    public void onCandle(StrategyContext ctx) {
        Candle c = ctx.candle();
        if (!c.symbol().equals(symbol)) return;
        if (nextBuyAt == null) nextBuyAt = c.timestamp();
        if (c.timestamp().isBefore(nextBuyAt)) return;

        BigDecimal price = c.close();
        if (price.signum() <= 0) return;

        BigDecimal qty = usdPerBuy.divide(price, MC);
        if (!ctx.portfolio().hasCash(usdPerBuy)) return;

        ctx.router().submit(Order.marketBuy(symbol, qty, c.timestamp()));
        nextBuyAt = c.timestamp().plus(Duration.ofDays(intervalDays));
    }
}
