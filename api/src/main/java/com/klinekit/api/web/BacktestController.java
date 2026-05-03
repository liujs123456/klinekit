package com.klinekit.api.web;

import com.klinekit.api.dto.BacktestRequest;
import com.klinekit.api.dto.BacktestRunSummaryDto;
import com.klinekit.api.dto.EquityPointDto;
import com.klinekit.api.dto.TradeDto;
import com.klinekit.api.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class BacktestController {

    private final BacktestService service;

    public BacktestController(BacktestService service) {
        this.service = service;
    }

    @PostMapping("/backtest")
    public ResponseEntity<BacktestRunSummaryDto> run(@RequestBody BacktestRequest req) {
        BacktestRunSummaryDto summary = service.runAndPersist(req);
        return ResponseEntity.created(URI.create("/api/v1/runs/" + summary.id())).body(summary);
    }

    @GetMapping("/runs")
    public List<BacktestRunSummaryDto> list() {
        return service.listRuns();
    }

    @GetMapping("/runs/{id}")
    public BacktestRunSummaryDto get(@PathVariable UUID id) {
        return service.findRun(id);
    }

    @GetMapping("/runs/{id}/trades")
    public List<TradeDto> trades(@PathVariable UUID id) {
        return service.findTrades(id);
    }

    @GetMapping("/runs/{id}/equity-curve")
    public List<EquityPointDto> equityCurve(@PathVariable UUID id) {
        return service.findEquityCurve(id);
    }
}
