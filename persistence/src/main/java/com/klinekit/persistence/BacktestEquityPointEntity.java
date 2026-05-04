package com.klinekit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backtest_equity_point")
public class BacktestEquityPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(nullable = false)
    private BigDecimal equity;

    @Column(nullable = false, length = 16)
    private String kind = "STRATEGY";

    public BacktestEquityPointEntity() {}

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Long getId() { return id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public BigDecimal getEquity() { return equity; }
    public void setEquity(BigDecimal equity) { this.equity = equity; }
}
