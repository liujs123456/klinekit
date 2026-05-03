package com.klinekit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "backtest_run")
public class BacktestRunEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String strategy;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "initial_cash", nullable = false)
    private BigDecimal initialCash;

    @Column(name = "final_equity", nullable = false)
    private BigDecimal finalEquity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metrics;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BacktestRunEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public BigDecimal getInitialCash() { return initialCash; }
    public void setInitialCash(BigDecimal initialCash) { this.initialCash = initialCash; }
    public BigDecimal getFinalEquity() { return finalEquity; }
    public void setFinalEquity(BigDecimal finalEquity) { this.finalEquity = finalEquity; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public Instant getCreatedAt() { return createdAt; }
}
