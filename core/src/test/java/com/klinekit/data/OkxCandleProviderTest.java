package com.klinekit.data;

import com.klinekit.domain.Candle;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OkxCandleProviderTest {

    private HttpServer server;
    private final List<URI> received = new ArrayList<>();
    private final AtomicReference<String> nextResponse = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestURI());
            byte[] body = nextResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("content-type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void normaliseInstId_addsDashForKnownQuote() {
        assertThat(OkxCandleProvider.normaliseInstId("BTCUSDT")).isEqualTo("BTC-USDT");
        assertThat(OkxCandleProvider.normaliseInstId("eth/usdt")).isEqualTo("ETH-USDT");
        assertThat(OkxCandleProvider.normaliseInstId("BTC-USDT")).isEqualTo("BTC-USDT");
    }

    @Test
    void parsesAndSortsCandlesAndSkipsUnconfirmed() {
        // OKX returns newest-first; one row has confirm=0 and must be skipped
        nextResponse.set("""
                {"code":"0","msg":"","data":[
                  ["1700006400000","100.5","102.0","99.0","101.0","123","1230","1230","0"],
                  ["1699920000000","99.0","101.5","98.5","100.5","150","1500","1500","1"],
                  ["1699833600000","98.0","99.5","97.5","99.0","140","1400","1400","1"]
                ]}
                """);

        var provider = providerThatPointsAtTestServer("BTC-USDT", "1D", 5);
        List<Candle> candles = provider.load();
        assertThat(candles).hasSize(2);
        // sorted ascending
        assertThat(candles.get(0).timestamp()).isBefore(candles.get(1).timestamp());
        assertThat(candles.get(0).symbol()).isEqualTo("BTCUSDT");
        assertThat(candles.get(0).close()).isEqualByComparingTo("99.0");
        assertThat(candles.get(1).close()).isEqualByComparingTo("100.5");
    }

    @Test
    void respectsCountAcrossPagination() {
        // First call returns 2 confirmed candles; provider stops once count satisfied.
        nextResponse.set("""
                {"code":"0","msg":"","data":[
                  ["1699920000000","99.0","101.5","98.5","100.5","150","1500","1500","1"],
                  ["1699833600000","98.0","99.5","97.5","99.0","140","1400","1400","1"]
                ]}
                """);
        var provider = providerThatPointsAtTestServer("BTC-USDT", "1D", 2);
        List<Candle> candles = provider.load();
        assertThat(candles).hasSize(2);
        assertThat(received).hasSize(1); // one page sufficed
    }

    @Test
    void surfacesOkxErrorPayload() {
        nextResponse.set("""
                {"code":"50001","msg":"instrument not found","data":[]}
                """);
        var provider = providerThatPointsAtTestServer("FAKE-USDT", "1D", 3);
        try {
            provider.load();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("50001").contains("instrument not found");
            return;
        }
        throw new AssertionError("expected an exception");
    }

    private OkxCandleProvider providerThatPointsAtTestServer(String symbol, String bar, int count) {
        // We point our provider at the local test server by overriding the base URL via reflection.
        // The provider's BASE_URL is private static final; rather than reflect, we use a
        // narrowed HttpClient that rewrites the host to the test server address.
        HttpClient redirecting = new RedirectingHttpClient(server.getAddress());
        return new OkxCandleProvider(symbol, bar, count, redirecting);
    }

    /** Wraps a real HttpClient so requests to www.okx.com hit our test server instead. */
    private static final class RedirectingHttpClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        private final InetSocketAddress targetServer;
        RedirectingHttpClient(InetSocketAddress target) { this.targetServer = target; }

        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public java.util.Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return delegate.proxy(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return delegate.sslContext(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return delegate.executor(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            return delegate.send(rewrite(req), handler);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req, HttpResponse.BodyHandler<T> handler) {
            return delegate.sendAsync(rewrite(req), handler);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> push) {
            return delegate.sendAsync(rewrite(req), handler, push);
        }

        private HttpRequest rewrite(HttpRequest req) {
            URI u = req.uri();
            URI rewritten = URI.create("http://" + targetServer.getHostString() + ":" + targetServer.getPort() + u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery()));
            HttpRequest.Builder b = HttpRequest.newBuilder(rewritten).method(req.method(),
                    req.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
            req.headers().map().forEach((k, vs) -> {
                if (k.equalsIgnoreCase("Host") || k.equalsIgnoreCase("Connection")) return;
                vs.forEach(v -> b.header(k, v));
            });
            req.timeout().ifPresent(b::timeout);
            return b.build();
        }
    }
}
