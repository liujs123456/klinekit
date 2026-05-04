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
        OrderStatus status,
        Direction direction,
        BigDecimal leverage,
        boolean reduceOnly
) {
    public Order {
        if (direction == null) direction = Direction.LONG;
        if (leverage == null) leverage = BigDecimal.ONE;
    }

    /** Spot market buy (LONG, leverage = 1). */
    public static Order marketBuy(String symbol, BigDecimal quantity, Instant submittedAt) {
        return new Order(UUID.randomUUID().toString(), symbol, Side.BUY, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING,
                Direction.LONG, BigDecimal.ONE, false);
    }

    /** Spot market sell (closes a LONG, leverage = 1). */
    public static Order marketSell(String symbol, BigDecimal quantity, Instant submittedAt) {
        return new Order(UUID.randomUUID().toString(), symbol, Side.SELL, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING,
                Direction.LONG, BigDecimal.ONE, true);
    }

    /** Open a perp position in the given direction at market. */
    public static Order openPerp(String symbol, Direction direction, BigDecimal quantity,
                                  BigDecimal leverage, Instant submittedAt) {
        Side side = direction == Direction.LONG ? Side.BUY : Side.SELL;
        return new Order(UUID.randomUUID().toString(), symbol, side, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING,
                direction, leverage, false);
    }

    /** Close (some / all of) a perp position at market. */
    public static Order closePerp(String symbol, Direction direction, BigDecimal quantity,
                                   BigDecimal leverage, Instant submittedAt) {
        Side side = direction == Direction.LONG ? Side.SELL : Side.BUY;
        return new Order(UUID.randomUUID().toString(), symbol, side, OrderType.MARKET,
                quantity, null, submittedAt, OrderStatus.PENDING,
                direction, leverage, true);
    }

    public boolean isPerp() {
        return leverage != null && leverage.compareTo(BigDecimal.ONE) > 0;
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, symbol, side, type, quantity, limitPrice, submittedAt, newStatus,
                direction, leverage, reduceOnly);
    }
}
