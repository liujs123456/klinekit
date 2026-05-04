package com.klinekit.api.service;

import com.klinekit.api.dto.BacktestRequest;
import com.klinekit.api.dto.BacktestRunSummaryDto;
import com.klinekit.api.dto.CandleDto;
import com.klinekit.api.dto.EquityPointDto;
import com.klinekit.api.dto.TradeDto;
import com.klinekit.data.OkxCandleProvider;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        BacktestEngine.Config cfg = new BacktestEngine.Config(
                req.initialCash() == null ? new BigDecimal("10000") : req.initialCash(),
                req.feeBps() == null ? new BigDecimal("10") : req.feeBps(),
                req.slippageBps() == null ? new BigDecimal("5") : req.slippageBps()
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
