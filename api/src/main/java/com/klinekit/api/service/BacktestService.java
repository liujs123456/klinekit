package com.klinekit.api.service;

import com.klinekit.api.dto.BacktestRequest;
import com.klinekit.api.dto.BacktestRunSummaryDto;
import com.klinekit.api.dto.CandleDto;
import com.klinekit.api.dto.EquityPointDto;
import com.klinekit.api.dto.TradeDto;
import com.klinekit.data.OkxCandleProvider;
import com.klinekit.data.OkxFundingRateProvider;
import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.engine.BacktestEngine;
import com.klinekit.persistence.BacktestEquityPointEntity;
import com.klinekit.persistence.BacktestEquityPointRepository;
import com.klinekit.persistence.BacktestRunEntity;
import com.klinekit.persistence.BacktestRunRepository;
import com.klinekit.persistence.BacktestTradeEntity;
import com.klinekit.persistence.BacktestTradeRepository;
import com.klinekit.strategy.Strategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class BacktestService {

    private final StrategyFactory strategyFactory;
    private final BacktestRunRepository runRepo;
    private final BacktestTradeRepository tradeRepo;
    private final BacktestEquityPointRepository equityRepo;

    public BacktestService(
            StrategyFactory strategyFactory,
            BacktestRunRepository runRepo,
            BacktestTradeRepository tradeRepo,
            BacktestEquityPointRepository equityRepo
    ) {
        this.strategyFactory = strategyFactory;
        this.runRepo = runRepo;
        this.tradeRepo = tradeRepo;
        this.equityRepo = equityRepo;
    }

    @Transactional
    public BacktestRunSummaryDto runAndPersist(BacktestRequest req) {
        String symbol = req.symbol() == null ? "UNKNOWN" : req.symbol();
        List<Candle> candles;
        if (req.source() != null && req.source().provider() != null) {
            candles = loadFromSource(req.source(), symbol);
            if (candles.isEmpty()) {
                throw new IllegalArgumentException("data source returned no candles");
            }
            symbol = candles.getFirst().symbol();
        } else {
            if (req.candles() == null || req.candles().isEmpty()) {
                throw new IllegalArgumentException("candles must not be empty (or specify a 'source')");
            }
            candles = toCandles(req.candles(), symbol);
        }

        Strategy strategy = strategyFactory.build(req.strategy(), symbol, req.params());

        BigDecimal funding = extractFundingRate(req.params());
        NavigableMap<Instant, BigDecimal> fundingHistory = maybeFetchFundingHistory(req.params(), symbol);
        BacktestEngine.Config cfg = new BacktestEngine.Config(
                req.initialCash() == null ? new BigDecimal("10000") : req.initialCash(),
                req.feeBps() == null ? new BigDecimal("10") : req.feeBps(),
                req.slippageBps() == null ? new BigDecimal("5") : req.slippageBps(),
                funding,
                fundingHistory
        );
        BacktestResult result = new BacktestEngine(cfg).run(strategy, candles);

        BacktestRunEntity run = new BacktestRunEntity();
        run.setId(UUID.randomUUID());
        run.setStrategy(result.strategy());
        run.setSymbol(result.symbol());
        run.setStartAt(result.start());
        run.setEndAt(result.end());
        run.setInitialCash(result.initialCash());
        run.setFinalEquity(result.finalEquity());
        run.setConfig(new HashMap<>(strategy.config()));
        run.setMetrics(toObjectMap(result.metrics()));
        BacktestRunEntity saved = runRepo.save(run);

        List<BacktestTradeEntity> tradeEntities = new ArrayList<>(result.trades().size());
        for (int i = 0; i < result.trades().size(); i++) {
            var t = result.trades().get(i);
            BacktestTradeEntity e = new BacktestTradeEntity();
            e.setRunId(saved.getId());
            e.setSeq(i);
            e.setOrderId(t.orderId());
            e.setSymbol(t.symbol());
            e.setSide(t.side().name());
            e.setQuantity(t.quantity());
            e.setPrice(t.price());
            e.setFee(t.fee());
            e.setExecutedAt(t.executedAt());
            tradeEntities.add(e);
        }
        tradeRepo.saveAll(tradeEntities);

        List<BacktestEquityPointEntity> eqEntities = new ArrayList<>(result.equityCurve().size());
        for (int i = 0; i < result.equityCurve().size(); i++) {
            var p = result.equityCurve().get(i);
            BacktestEquityPointEntity e = new BacktestEquityPointEntity();
            e.setRunId(saved.getId());
            e.setSeq(i);
            e.setTs(p.timestamp());
            e.setEquity(p.equity());
            eqEntities.add(e);
        }
        equityRepo.saveAll(eqEntities);

        return toSummary(saved, result.trades().size(), result.equityCurve().size());
    }

    /**
     * Run many backtest configurations in parallel using one virtual thread per
     * job. Each job opens its own transaction so partial successes don't roll
     * each other back. Returns the summaries in the input order.
     */
    public List<BacktestRunSummaryDto> runBatch(List<BacktestRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BacktestRunSummaryDto>> futures = new ArrayList<>(requests.size());
            for (BacktestRequest req : requests) {
                futures.add(executor.submit(() -> runAndPersist(req)));
            }
            List<BacktestRunSummaryDto> results = new ArrayList<>(requests.size());
            for (Future<BacktestRunSummaryDto> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException re) throw re;
                    throw new RuntimeException(cause);
                }
            }
            return results;
        }
    }

    @Transactional(readOnly = true)
    public BacktestRunSummaryDto findRun(UUID id) {
        BacktestRunEntity run = runRepo.findById(id).orElseThrow(() -> new NotFoundException("run not found"));
        return toSummary(run,
                tradeRepo.findByRunIdOrderBySeqAsc(id).size(),
                equityRepo.findByRunIdOrderBySeqAsc(id).size());
    }

    @Transactional(readOnly = true)
    public List<BacktestRunSummaryDto> listRuns() {
        return runRepo.findTop50ByOrderByCreatedAtDesc().stream()
                .map(r -> toSummary(r, 0, 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TradeDto> findTrades(UUID id) {
        return tradeRepo.findByRunIdOrderBySeqAsc(id).stream()
                .map(t -> new TradeDto(t.getSeq(), t.getOrderId(), t.getSymbol(), t.getSide(),
                        t.getQuantity(), t.getPrice(), t.getFee(), t.getExecutedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquityPointDto> findEquityCurve(UUID id) {
        return equityRepo.findByRunIdOrderBySeqAsc(id).stream()
                .map(p -> new EquityPointDto(p.getSeq(), p.getTs(), p.getEquity()))
                .toList();
    }

    private static NavigableMap<Instant, BigDecimal> maybeFetchFundingHistory(
            Map<String, Object> params, String symbol) {
        if (params == null) return null;
        Object flag = params.get("useLiveFundingHistory");
        if (flag == null) return null;
        boolean enabled = Boolean.parseBoolean(flag.toString());
        if (!enabled) return null;
        try {
            int count = 200;
            Object countRaw = params.get("fundingHistoryCount");
            if (countRaw != null) count = Integer.parseInt(countRaw.toString());
            return new OkxFundingRateProvider(symbol, count).load();
        } catch (RuntimeException e) {
            // Don't fail the whole backtest just because OKX funding fetch failed —
            // fall back to fixed rate.
            return null;
        }
    }

    private static BigDecimal extractFundingRate(Map<String, Object> params) {
        if (params == null) return BigDecimal.ZERO;
        Object raw = params.get("fundingRatePer8h");
        if (raw == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static List<Candle> loadFromSource(BacktestRequest.DataSourceSpec spec, String fallbackSymbol) {
        String provider = spec.provider().toLowerCase();
        return switch (provider) {
            case "okx" -> {
                String sym = spec.symbol() != null ? spec.symbol() : fallbackSymbol;
                String bar = spec.bar() != null ? spec.bar() : "1D";
                int count = spec.count() != null ? spec.count() : 365;
                if (count <= 0 || count > 5000) {
                    throw new IllegalArgumentException("source.count must be between 1 and 5000");
                }
                yield new OkxCandleProvider(sym, bar, count).load();
            }
            default -> throw new IllegalArgumentException("unknown data source: " + spec.provider());
        };
    }

    private static List<Candle> toCandles(List<CandleDto> dtos, String symbol) {
        List<Candle> out = new ArrayList<>(dtos.size());
        for (CandleDto d : dtos) {
            BigDecimal vol = d.volume() == null ? BigDecimal.ZERO : d.volume();
            out.add(new Candle(symbol, d.timestamp(), d.open(), d.high(), d.low(), d.close(), vol));
        }
        return out;
    }

    private static Map<String, Object> toObjectMap(Map<String, BigDecimal> in) {
        Map<String, Object> out = new HashMap<>(in.size());
        for (var e : in.entrySet()) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static BacktestRunSummaryDto toSummary(BacktestRunEntity r, int trades, int eq) {
        return new BacktestRunSummaryDto(
                r.getId(), r.getStrategy(), r.getSymbol(),
                r.getStartAt(), r.getEndAt(),
                r.getInitialCash(), r.getFinalEquity(),
                r.getConfig(), r.getMetrics(),
                r.getCreatedAt(),
                trades, eq);
    }
}
