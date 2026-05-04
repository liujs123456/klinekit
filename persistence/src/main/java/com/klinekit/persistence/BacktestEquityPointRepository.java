package com.klinekit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BacktestEquityPointRepository extends JpaRepository<BacktestEquityPointEntity, Long> {

    List<BacktestEquityPointEntity> findByRunIdOrderBySeqAsc(UUID runId);

    List<BacktestEquityPointEntity> findByRunIdAndKindOrderBySeqAsc(UUID runId, String kind);
}
