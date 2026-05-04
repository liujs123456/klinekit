package com.klinekit.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchBacktestTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void runsThreeBacktestsInParallelAndReturnsSummaries() throws Exception {
        List<Map<String, Object>> body = List.of(
                req("dca", Map.of("usdPerBuy", "100", "intervalDays", "7")),
                req("dca", Map.of("usdPerBuy", "200", "intervalDays", "7")),
                req("dca", Map.of("usdPerBuy", "100", "intervalDays", "1")));

        long start = System.currentTimeMillis();
        var raw = mvc.perform(post("/api/v1/backtest/batch")
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(raw.getStatus()).isEqualTo(200);
        List<Map<String, Object>> results = json.readValue(raw.getContentAsString(),
                new TypeReference<>() {});
        assertThat(results).hasSize(3);
        assertThat(results).allSatisfy(r -> assertThat(r.get("id")).isNotNull());
        assertThat(results).extracting(r -> r.get("strategy"))
                .containsOnly("spot.dca");
        // Smoke check that we used parallel execution — three jobs should be < 3x a
        // single-run wall-clock; we can't assert too tightly without flake risk,
        // so just confirm it returned in a sensible budget.
        assertThat(elapsed).isLessThan(5000);
    }

    private Map<String, Object> req(String strategy, Map<String, Object> params) {
        return Map.of(
                "strategy", strategy,
                "symbol", "BTCUSDT",
                "initialCash", "10000",
                "feeBps", "0",
                "slippageBps", "0",
                "params", params,
                "candles", makeCandles(60));
    }

    private static List<Map<String, Object>> makeCandles(int days) {
        List<Map<String, Object>> out = new ArrayList<>(days);
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < days; i++) {
            BigDecimal px = BigDecimal.valueOf(100 + i);
            out.add(Map.of(
                    "timestamp", t.plus(i, ChronoUnit.DAYS).toString(),
                    "open", px,
                    "high", px,
                    "low", px,
                    "close", px,
                    "volume", BigDecimal.ZERO));
        }
        return out;
    }
}
