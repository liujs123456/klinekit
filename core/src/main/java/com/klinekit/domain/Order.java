package com.klinekit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(
        String id,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice,
        Instant submittedAt,
        OrderStatus status
) {
    public static Order marketBuy(String symbol, BigDecimal quantity, Instant submittedAt) {
        return new Order(UUID.randomUUID().toString(), symbol, Side.BUY, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING);
    }

    public static Order marketSell(String symbol, BigDecimal quantity, Instant submittedAt) {
        return new Order(UUID.randomUUID().toString(), symbol, Side.SELL, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING);
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, symbol, side, type, quantity, limitPrice, submittedAt, newStatus);
    }
}
