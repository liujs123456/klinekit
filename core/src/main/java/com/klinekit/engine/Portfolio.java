package com.klinekit.engine;

import com.klinekit.domain.Direction;
import com.klinekit.domain.Order;
import com.klinekit.domain.Position;
import com.klinekit.domain.Trade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Portfolio {

    private static final MathContext MC = MathContext.DECIMAL64;

    private BigDecimal cash;
    private final Map<String, Position> positions = new HashMap<>();

    public Portfolio(BigDecimal initialCash) {
        this.cash = initialCash;
    }

    public BigDecimal cash() {
        return cash;
    }

    public Position position(String symbol) {
        return positions.getOrDefault(symbol, Position.empty(symbol));
    }

    public Map<String, Position> positions() {
        return Collections.unmodifiableMap(positions);
    }

    /** Spot fill — applies a Trade (BUY = open long, SELL = close long) at full notional cost. */
    public void apply(Trade trade) {
        cash = cash.add(trade.cashFlow());
        Position p = position(trade.symbol());
        Position updated = switch (trade.side()) {
            case BUY -> p.applyBuy(trade.quantity(), trade.price());
            case SELL -> p.applySell(trade.quantity());
        };
        if (updated.isFlat()) {
            positions.remove(trade.symbol());
        } else {
            positions.put(trade.symbol(), updated);
        }
    }

    /**
     * Perp fill: opens or closes a perp position with isolated margin.
     *
     * <p>Open: posts {@code notional / leverage} of margin from cash plus the fee.
     * Close: returns margin proportional to closed quantity, plus realised PnL, minus the fee.
     */
    public Trade applyPerpFill(Order order, BigDecimal fillPrice, BigDecimal fee, Instant ts) {
        Position pos = positions.getOrDefault(
                order.symbol(),
                Position.emptyPerp(order.symbol(), order.direction(), order.leverage()));
        BigDecimal qty = order.quantity();
        BigDecimal notional = qty.multiply(fillPrice);
        Position updated;
        if (order.reduceOnly()) {
            // Close: return margin proportional to qty closed + realised pnl, minus fee.
            BigDecimal margin = pos.avgCost().multiply(qty).divide(pos.leverage(), MC);
            BigDecimal realised = pos.realisedPnlOnClose(qty, fillPrice);
            cash = cash.add(margin).add(realised).subtract(fee);
            updated = pos.applyClose(qty);
        } else {
            // Open: post margin + fee out of cash.
            BigDecimal margin = notional.divide(order.leverage(), MC);
            cash = cash.subtract(margin).subtract(fee);
            updated = pos.applyOpen(qty, fillPrice);
        }
        if (updated.isFlat()) {
            positions.remove(order.symbol());
        } else {
            positions.put(order.symbol(), updated);
        }
        return new Trade(order.id(), order.symbol(), order.side(), qty, fillPrice, fee, ts);
    }

    /** Add cash from a funding-rate payment (negative = paid, positive = received). */
    public void applyFundingPayment(BigDecimal cashDelta) {
        if (cashDelta.signum() != 0) {
            cash = cash.add(cashDelta);
        }
    }

    /**
     * Force-close a perp position at its liquidation price. Margin is forfeit;
     * realised PnL is exactly -margin (so cash unchanged from where it was when
     * the position was opened — caller updates cash to reflect that the margin
     * was already deducted on open).
     */
    public void applyLiquidation(String symbol) {
        positions.remove(symbol);
    }

    public BigDecimal equity(Map<String, BigDecimal> markPrices) {
        BigDecimal eq = cash;
        for (Position p : positions.values()) {
            BigDecimal mark = markPrices.get(p.symbol());
            if (mark == null) continue;
            if (p.isPerp()) {
                BigDecimal margin = p.avgCost().multiply(p.quantity()).divide(p.leverage(), MC);
                BigDecimal upnl = p.unrealizedPnl(mark);
                eq = eq.add(margin).add(upnl);
            } else if (p.direction() == Direction.LONG) {
                eq = eq.add(p.marketValue(mark));
            }
        }
        return eq;
    }

    public BigDecimal equity(String symbol, BigDecimal markPrice) {
        return equity(Map.of(symbol, markPrice));
    }

    public boolean hasCash(BigDecimal amount) {
        return cash.compareTo(amount) >= 0;
    }
}
