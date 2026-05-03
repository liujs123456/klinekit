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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BacktestControllerH2Test {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void runBacktestPersistsAndExposesEquityCurve() throws Exception {
        var req = Map.of(
                "strategy", "dca",
                "symbol", "BTCUSDT",
                "initialCash", "10000",
                "feeBps", "0",
                "slippageBps", "0",
                "params", Map.of("usdPerBuy", "100", "intervalDays", "7"),
                "candles", makeCandles(60));

        var body = json.writeValueAsString(req);
        var res = mvc.perform(post("/api/v1/backtest")
                        .contentType("application/json")
                        .content(body))
                .andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(201);
        Map<String, Object> summary = json.readValue(res.getContentAsString(), new TypeReference<>() {});
        String id = (String) summary.get("id");
        assertThat(id).isNotBlank();
        assertThat(summary.get("strategy")).isEqualTo("spot.dca");

        var listRes = mvc.perform(get("/api/v1/runs"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> runs = json.readValue(listRes, new TypeReference<>() {});
        assertThat(runs).extracting("id").contains(id);

        var curveRes = mvc.perform(get("/api/v1/runs/" + id + "/equity-curve"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> curve = json.readValue(curveRes, new TypeReference<>() {});
        assertThat(curve).hasSize(60);

        var tradesRes = mvc.perform(get("/api/v1/runs/" + id + "/trades"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> trades = json.readValue(tradesRes, new TypeReference<>() {});
        assertThat(trades).isNotEmpty();
        assertThat(trades.getFirst().get("side")).isEqualTo("BUY");
    }

    @Test
    void getNonExistentRunReturns404() throws Exception {
        int status = mvc.perform(get("/api/v1/runs/00000000-0000-0000-0000-000000000000"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(404);
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
