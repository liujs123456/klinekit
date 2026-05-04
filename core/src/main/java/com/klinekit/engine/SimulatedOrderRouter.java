package com.klinekit.engine;

import com.klinekit.domain.Candle;
import com.klinekit.domain.Order;
import com.klinekit.domain.OrderType;
import com.klinekit.domain.Trade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class SimulatedOrderRouter implements OrderRouter {

    private final Portfolio portfolio;
    private final BigDecimal feeBps;
    private final BigDecimal slippageBps;
    private final List<Trade> trades = new ArrayList<>();
    private Candle currentCandle;

    public SimulatedOrderRouter(Portfolio portfolio, BigDecimal feeBps, BigDecimal slippageBps) {
        this.portfolio = portfolio;
        this.feeBps = feeBps;
        this.slippageBps = slippageBps;
    }

    void onCandle(Candle c) {
        this.currentCandle = c;
    }

    @Override
    public Trade submit(Order order) {
        if (currentCandle == null) {
            throw new IllegalStateException("no current candle — engine not started");
        }
        if (order.type() != OrderType.MARKET) {
            throw new UnsupportedOperationException("only MARKET orders are supported");
        }

        BigDecimal mid = currentCandle.close();
        BigDecimal slipFactor = switch (order.side()) {
            case BUY -> BigDecimal.ONE.add(slippageBps.movePointLeft(4));
            case SELL -> BigDecimal.ONE.subtract(slippageBps.movePointLeft(4));
        };
        BigDecimal fillPrice = mid.multiply(slipFactor);
        BigDecimal notional = fillPrice.multiply(order.quantity());
        BigDecimal fee = notional.multiply(feeBps.movePointLeft(4)).abs();

        Trade trade;
        if (order.isPerp()) {
            trade = portfolio.applyPerpFill(order, fillPrice, fee, currentCandle.timestamp());
        } else {
            trade = new Trade(
                    order.id(), order.symbol(), order.side(), order.quantity(),
                    fillPrice, fee, currentCandle.timestamp());
            portfolio.apply(trade);
        }
        trades.add(trade);
        return trade;
    }

    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    /**
     * Record a synthetic liquidation event in the trade log so it can be rendered
     * downstream (dashboard, persistence, metrics) without a real order being placed.
     * The orderId is prefixed with {@code LIQ-} so consumers can filter / mark them.
     */
    void recordLiquidation(com.klinekit.domain.Position position, Candle candle, BigDecimal liquidationPrice) {
        com.klinekit.domain.Side side = position.direction() == com.klinekit.domain.Direction.LONG
                ? com.klinekit.domain.Side.SELL
                : com.klinekit.domain.Side.BUY;
        Trade liq = new Trade(
                "LIQ-" + position.symbol() + "-" + candle.timestamp().toEpochMilli(),
                position.symbol(),
                side,
                position.quantity(),
                liquidationPrice,
                BigDecimal.ZERO,
                candle.timestamp());
        trades.add(liq);
    }
}
