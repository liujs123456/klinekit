package com.klinekit.engine;

import com.klinekit.domain.Position;
import com.klinekit.domain.Trade;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Portfolio {

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

    public BigDecimal equity(Map<String, BigDecimal> markPrices) {
        BigDecimal eq = cash;
        for (Position p : positions.values()) {
            BigDecimal mark = markPrices.get(p.symbol());
            if (mark != null) {
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
