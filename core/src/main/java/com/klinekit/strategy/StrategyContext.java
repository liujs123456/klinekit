package com.klinekit.strategy;

import com.klinekit.domain.Candle;
import com.klinekit.engine.OrderRouter;
import com.klinekit.engine.Portfolio;

public record StrategyContext(
        Candle candle,
        Portfolio portfolio,
        OrderRouter router
) {}
