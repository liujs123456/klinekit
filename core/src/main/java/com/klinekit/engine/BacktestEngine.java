package com.klinekit.engine;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.domain.EquityPoint;
import com.klinekit.domain.Position;
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
            BigDecimal slippageBps,
            BigDecimal fundingRatePer8h
    ) {
        public Config(BigDecimal initialCash, BigDecimal feeBps, BigDecimal slippageBps) {
            this(initialCash, feeBps, slippageBps, BigDecimal.ZERO);
        }

        public static Config defaults() {
            return new Config(new BigDecimal("10000"), new BigDecimal("10"), new BigDecimal("5"), BigDecimal.ZERO);
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
        FundingRateSim funding = new FundingRateSim(config.fundingRatePer8h());

        Candle first = candles.getFirst();
        router.onCandle(first);
        funding.onCandle(first);
        strategy.onStart(new StrategyContext(first, portfolio, router));

        List<EquityPoint> curve = new ArrayList<>(candles.size());
        Instant start = first.timestamp();
        Instant end = first.timestamp();
        String symbol = first.symbol();

        for (Candle c : candles) {
            router.onCandle(c);
            funding.onCandle(c);

            // Liquidation pass — close any perp position whose liq price was crossed this candle
            for (Position p : List.copyOf(portfolio.positions().values())) {
                if (LiquidationCalculator.wasLiquidated(p, c.high(), c.low())) {
                    portfolio.applyLiquidation(p.symbol());
                }
            }

            // Funding accrual pass — for each open perp position, debit/credit cash
            for (Position p : portfolio.positions().values()) {
                BigDecimal mark = p.symbol().equals(c.symbol()) ? c.close() : null;
                if (mark != null && p.isPerp()) {
                    BigDecimal payment = funding.accrue(p, mark, c.timestamp());
                    portfolio.applyFundingPayment(payment);
                }
            }

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
