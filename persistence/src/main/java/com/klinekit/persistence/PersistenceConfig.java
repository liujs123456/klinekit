package com.klinekit.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.klinekit.persistence")
@EntityScan(basePackages = "com.klinekit.persistence")
public class PersistenceConfig {
}
