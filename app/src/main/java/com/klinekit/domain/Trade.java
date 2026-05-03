package com.klinekit.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Trade(
        String orderId,
        String symbol,
        Side side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        Instant executedAt
) {
    public BigDecimal notional() {
        return price.multiply(quantity);
    }

    public BigDecimal cashFlow() {
        BigDecimal n = notional();
        return switch (side) {
            case BUY -> n.add(fee).negate();
            case SELL -> n.subtract(fee);
        };
    }
}
