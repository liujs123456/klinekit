package com.klinekit.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.klinekit.persistence.PersistenceConfig;

@SpringBootApplication
@Import(PersistenceConfig.class)
public class KlinekitApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlinekitApplication.class, args);
    }
}
