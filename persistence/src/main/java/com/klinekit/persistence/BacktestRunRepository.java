package com.klinekit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BacktestRunRepository extends JpaRepository<BacktestRunEntity, UUID> {

    List<BacktestRunEntity> findTop50ByOrderByCreatedAtDesc();

    List<BacktestRunEntity> findBySymbolOrderByCreatedAtDesc(String symbol);
}
