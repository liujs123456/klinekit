package com.klinekit.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klinekit.domain.Candle;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OkxCandleProvider implements CandleProvider {

    private static final String BASE_URL = "https://www.okx.com/api/v5/market/history-candles";
    private static final int PAGE_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String instId;
    private final String bar;
    private final int count;
    private final HttpClient http;

    public OkxCandleProvider(String symbol, String bar, int count) {
        this(symbol, bar, count, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OkxCandleProvider(String symbol, String bar, int count, HttpClient http) {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        this.instId = normaliseInstId(symbol);
        this.bar = bar;
        this.count = count;
        this.http = http;
    }

    @Override
    public List<Candle> load() {
        List<Candle> out = new ArrayList<>(count);
        Long oldestTs = null;
        String klineSymbol = instId.replace("-", "");

        while (out.size() < count) {
            int batch = Math.min(PAGE_LIMIT, count - out.size());
            JsonNode data = fetchPage(batch, oldestTs);
            if (data == null || data.isEmpty()) break;

            for (JsonNode row : data) {
                long tsMs = Long.parseLong(row.get(0).asText());
                String confirm = row.size() > 8 ? row.get(8).asText() : "1";
                if (!"1".equals(confirm)) continue;

                Candle c = new Candle(
                        klineSymbol,
                        Instant.ofEpochMilli(tsMs),
                        new BigDecimal(row.get(1).asText()),
                        new BigDecimal(row.get(2).asText()),
                        new BigDecimal(row.get(3).asText()),
                        new BigDecimal(row.get(4).asText()),
                        new BigDecimal(row.get(5).asText())
                );
                out.add(c);
                oldestTs = tsMs;
                if (out.size() >= count) break;
            }

            if (data.size() < batch) break;
        }

        out.sort(Comparator.comparing(Candle::timestamp));
        return List.copyOf(out);
    }

    private JsonNode fetchPage(int limit, Long beforeMs) {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?instId=").append(instId)
                .append("&bar=").append(bar)
                .append("&limit=").append(limit);
        if (beforeMs != null) url.append("&after=").append(beforeMs);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "klinekit/0.2 (+https://github.com/liujs123456/klinekit)")
                .GET()
                .build();

        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("OKX HTTP " + res.statusCode() + ": " + truncate(res.body()));
            }
            JsonNode root = JSON.readTree(res.body());
            String code = root.path("code").asText();
            if (!"0".equals(code)) {
                throw new IllegalStateException("OKX error code=" + code + " msg=" + root.path("msg").asText());
            }
            return root.path("data");
        } catch (IOException e) {
            throw new RuntimeException("OKX fetch failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OKX fetch interrupted", e);
        }
    }

    static String normaliseInstId(String symbol) {
        String s = symbol.toUpperCase().replace("/", "").replace("_", "");
        if (s.contains("-")) return s;
        // Split into base + quote — assume the suffix is the quote currency.
        for (String quote : new String[]{"USDT", "USDC", "USD", "BTC", "ETH"}) {
            if (s.endsWith(quote) && s.length() > quote.length()) {
                return s.substring(0, s.length() - quote.length()) + "-" + quote;
            }
        }
        return s;
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) + "..." : s);
    }
}
