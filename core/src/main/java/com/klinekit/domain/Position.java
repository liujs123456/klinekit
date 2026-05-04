package com.klinekit.domain;

import java.math.BigDecimal;
import java.math.MathContext;

public record Position(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCost,
        Direction direction,
        BigDecimal leverage
) {
    private static final MathContext MC = MathContext.DECIMAL64;
    public static final BigDecimal SPOT_LEVERAGE = BigDecimal.ONE;

    public Position {
        if (leverage == null || leverage.signum() <= 0) {
            throw new IllegalArgumentException("leverage must be positive");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
    }

    public static Position empty(String symbol) {
        return new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO, Direction.LONG, SPOT_LEVERAGE);
    }

    public static Position emptyPerp(String symbol, Direction direction, BigDecimal leverage) {
        return new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO, direction, leverage);
    }

    public boolean isFlat() {
        return quantity.signum() == 0;
    }

    public boolean isPerp() {
        return leverage.compareTo(BigDecimal.ONE) > 0;
    }

    public BigDecimal marketValue(BigDecimal price) {
        return quantity.multiply(price);
    }

    /** Unrealised PnL signed by direction. */
    public BigDecimal unrealizedPnl(BigDecimal price) {
        BigDecimal delta = price.subtract(avgCost);
        if (direction == Direction.SHORT) delta = delta.negate();
        return delta.multiply(quantity);
    }

    /** Increase the position in its direction; recomputes weighted-average entry. */
    public Position applyOpen(BigDecimal qty, BigDecimal price) {
        BigDecimal newQty = quantity.add(qty);
        BigDecimal newCost = quantity.multiply(avgCost).add(qty.multiply(price)).divide(newQty, MC);
        return new Position(symbol, newQty, newCost, direction, leverage);
    }

    /** Reduce the position toward zero. Returns the new state — caller computes PnL via realisedPnlOnClose. */
    public Position applyClose(BigDecimal qty) {
        BigDecimal newQty = quantity.subtract(qty);
        if (newQty.signum() == 0) {
            return new Position(symbol, BigDecimal.ZERO, BigDecimal.ZERO, direction, leverage);
        }
        return new Position(symbol, newQty, avgCost, direction, leverage);
    }

    /** Realised PnL when closing `qty` at `exitPrice`, signed by direction. */
    public BigDecimal realisedPnlOnClose(BigDecimal qty, BigDecimal exitPrice) {
        BigDecimal delta = exitPrice.subtract(avgCost);
        if (direction == Direction.SHORT) delta = delta.negate();
        return delta.multiply(qty);
    }

    // -------- backward-compat helpers for spot callers --------

    public Position applyBuy(BigDecimal qty, BigDecimal price) {
        return applyOpen(qty, price);
    }

    public Position applySell(BigDecimal qty) {
        return applyClose(qty);
    }
}
