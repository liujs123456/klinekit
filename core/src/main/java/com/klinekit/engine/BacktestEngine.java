package com.klinekit.engine;

import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.domain.EquityPoint;
import com.klinekit.domain.Position;
import com.klinekit.domain.Side;
import com.klinekit.domain.Trade;
import com.klinekit.metrics.Metrics;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.StrategyContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public final class BacktestEngine {

    public record Config(
            BigDecimal initialCash,
            BigDecimal feeBps,
            BigDecimal slippageBps,
            BigDecimal fundingRatePer8h,
            NavigableMap<Instant, BigDecimal> fundingRateHistory
    ) {
        public Config(BigDecimal initialCash, BigDecimal feeBps, BigDecimal slippageBps) {
            this(initialCash, feeBps, slippageBps, BigDecimal.ZERO, null);
        }

        public Config(BigDecimal initialCash, BigDecimal feeBps, BigDecimal slippageBps, BigDecimal fundingRatePer8h) {
            this(initialCash, feeBps, slippageBps, fundingRatePer8h, null);
        }

        public static Config defaults() {
            return new Config(new BigDecimal("10000"), new BigDecimal("10"), new BigDecimal("5"), BigDecimal.ZERO, null);
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
        FundingRateSim funding = new FundingRateSim(config.fundingRatePer8h(), config.fundingRateHistory());

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
                    BigDecimal liqPrice = LiquidationCalculator.liquidationPrice(p);
                    router.recordLiquidation(p, c, liqPrice);
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

        Map<String, BigDecimal> base = Metrics.compute(curve, router.trades(), config.initialCash()).asMap();
        Map<String, BigDecimal> metrics = new LinkedHashMap<>(base);

        BigDecimal totalInvested = computeTotalInvested(router.trades());
        BigDecimal holdingsValue = portfolio.position(symbol).marketValue(last.close());
        BigDecimal totalInjected = portfolio.totalInjected();
        metrics.put("totalInvested", totalInvested);
        metrics.put("holdingsValue", holdingsValue);
        metrics.put("totalInjected", totalInjected);

        // True ROI on capital actually deployed. Prefer injected (DCA realism)
        // when the strategy used auto-inject; otherwise use sum-of-buys.
        BigDecimal denom = totalInjected.signum() > 0 ? totalInjected : totalInvested;
        if (denom.signum() > 0) {
            BigDecimal numerator = totalInjected.signum() > 0
                    ? finalEquity.subtract(totalInjected)
                    : holdingsValue.subtract(totalInvested);
            metrics.put("roiOnInvestedPct",
                    numerator.divide(denom, MathContext.DECIMAL64)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP));
        } else {
            metrics.put("roiOnInvestedPct", BigDecimal.ZERO);
        }

        return new BacktestResult(
                strategy.name(),
                symbol,
                start,
                end,
                config.initialCash(),
                finalEquity,
                router.trades(),
                List.copyOf(curve),
                Map.copyOf(metrics)
        );
    }

    private static BigDecimal computeTotalInvested(List<Trade> trades) {
        BigDecimal total = BigDecimal.ZERO;
        for (Trade t : trades) {
            if (t.orderId() != null && t.orderId().startsWith("LIQ-")) continue;
            BigDecimal n = t.notional();
            total = t.side() == Side.BUY ? total.add(n) : total.subtract(n);
        }
        return total.signum() < 0 ? BigDecimal.ZERO : total;
    }
}
