package com.klinekit.engine;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.domain.EquityPoint;
import com.klinekit.metrics.Metrics;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BacktestEngine {

    public record Config(
            BigDecimal initialCash,
            BigDecimal feeBps,
            BigDecimal slippageBps
    ) {
        public static Config defaults() {
            return new Config(new BigDecimal("10000"), new BigDecimal("10"), new BigDecimal("5"));
        }
    }

    private final Config config;

    public BacktestEngine() {
        this(Config.defaults());
    }

    public BacktestEngine(Config config) {
        this.config = config;
    }

    public BacktestResult run(Strategy strategy, List<Candle> candles) {
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("no candles to backtest");
        }

        Portfolio portfolio = new Portfolio(config.initialCash());
        SimulatedOrderRouter router = new SimulatedOrderRouter(portfolio, config.feeBps(), config.slippageBps());

        Candle first = candles.getFirst();
        router.onCandle(first);
        StrategyContext startCtx = new StrategyContext(first, portfolio, router);
        strategy.onStart(startCtx);

        List<EquityPoint> curve = new ArrayList<>(candles.size());
        Instant start = first.timestamp();
        Instant end = first.timestamp();
        String symbol = first.symbol();

        for (Candle c : candles) {
            router.onCandle(c);
            strategy.onCandle(new StrategyContext(c, portfolio, router));
            BigDecimal eq = portfolio.equity(c.symbol(), c.close());
            curve.add(new EquityPoint(c.timestamp(), eq));
            end = c.timestamp();
        }

        Candle last = candles.getLast();
        router.onCandle(last);
        strategy.onFinish(new StrategyContext(last, portfolio, router));

        BigDecimal finalEquity = portfolio.equity(last.symbol(), last.close());
        Map<String, BigDecimal> metrics = Metrics.compute(curve, router.trades(), config.initialCash())
                .asMap();

        return new BacktestResult(
                strategy.name(),
                symbol,
                start,
                end,
                config.initialCash(),
                finalEquity,
                router.trades(),
                List.copyOf(curve),
                metrics
        );
    }

}
