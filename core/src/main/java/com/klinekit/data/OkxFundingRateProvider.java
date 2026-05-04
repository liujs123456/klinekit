package com.klinekit.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Fetches historical 8-hour funding rates for an OKX perpetual swap.
 *
 * <p>Returns a {@code NavigableMap<Instant, BigDecimal>} from {@code fundingTime}
 * to per-8h rate. Use {@code map.floorEntry(now)} to find the most recent rate
 * applicable at any candle timestamp.
 *
 * <p>OKX history-funding-rate is paginated newest-first via the {@code after}
 * cursor on {@code fundingTime}; we walk backwards to {@code count} entries.
 */
public final class OkxFundingRateProvider {

    private static final String BASE_URL = "https://www.okx.com/api/v5/public/funding-rate-history";
    private static final int PAGE_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String swapInstId;
    private final int count;
    private final HttpClient http;

    public OkxFundingRateProvider(String swapInstId, int count) {
        this(swapInstId, count, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OkxFundingRateProvider(String swapInstId, int count, HttpClient http) {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        this.swapInstId = normaliseSwapInstId(swapInstId);
        this.count = count;
        this.http = http;
    }

    public NavigableMap<Instant, BigDecimal> load() {
        NavigableMap<Instant, BigDecimal> out = new TreeMap<>();
        Long oldestMs = null;

        while (out.size() < count) {
            int batch = Math.min(PAGE_LIMIT, count - out.size());
            JsonNode data = fetchPage(batch, oldestMs);
            if (data == null || data.isEmpty()) break;

            for (JsonNode row : data) {
                long ts = Long.parseLong(row.get("fundingTime").asText());
                String rate = row.has("realizedRate") && !row.get("realizedRate").asText().isEmpty()
                        ? row.get("realizedRate").asText()
                        : row.get("fundingRate").asText();
                out.putIfAbsent(Instant.ofEpochMilli(ts), new BigDecimal(rate));
                oldestMs = ts;
                if (out.size() >= count) break;
            }
            if (data.size() < batch) break;
        }
        return out;
    }

    private JsonNode fetchPage(int limit, Long beforeMs) {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?instId=").append(swapInstId)
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
            throw new RuntimeException("OKX funding fetch failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OKX funding fetch interrupted", e);
        }
    }

    /** Accept BTC-USDT or BTC-USDT-SWAP, return BTC-USDT-SWAP. */
    static String normaliseSwapInstId(String s) {
        String up = s.toUpperCase().replace("/", "").replace("_", "");
        if (up.endsWith("-SWAP")) return up;
        if (up.contains("-")) return up + "-SWAP";
        for (String quote : new String[]{"USDT", "USDC", "USD"}) {
            if (up.endsWith(quote) && up.length() > quote.length()) {
                return up.substring(0, up.length() - quote.length()) + "-" + quote + "-SWAP";
            }
        }
        return up + "-SWAP";
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) + "..." : s);
    }
}
