package com.klinekit.domain;

import java.math.BigDecimal;
import java.math.MathContext;

public record Position(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCost
) {
    private static final MathContext MC = MathContext.DECIMAL64;

    public static Position empty(String symbol) {
        return new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean isFlat() {
        return quantity.signum() == 0;
    }

    public BigDecimal marketValue(BigDecimal price) {
        return quantity.multiply(price);
    }

    public BigDecimal unrealizedPnl(BigDecimal price) {
        return price.subtract(avgCost).multiply(quantity);
    }

    public Position applyBuy(BigDecimal qty, BigDecimal price) {
        BigDecimal newQty = quantity.add(qty);
        BigDecimal newCost = quantity.multiply(avgCost).add(qty.multiply(price)).divide(newQty, MC);
        return new Position(symbol, newQty, newCost);
    }

    public Position applySell(BigDecimal qty) {
        BigDecimal newQty = quantity.subtract(qty);
        if (newQty.signum() == 0) {
            return new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new Position(symbol, newQty, avgCost);
    }
}
