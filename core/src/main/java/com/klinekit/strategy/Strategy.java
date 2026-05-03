package com.klinekit.strategy;

import java.util.Map;

public interface Strategy {

    String name();

    Map<String, Object> config();

    void onCandle(StrategyContext ctx);

    default void onStart(StrategyContext ctx) {}

    default void onFinish(StrategyContext ctx) {}
}
