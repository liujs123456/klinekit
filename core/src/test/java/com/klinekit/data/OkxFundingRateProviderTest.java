package com.klinekit.data;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OkxFundingRateProviderTest {

    private HttpServer server;
    private final AtomicReference<String> nextResponse = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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
    void stop() { server.stop(0); }

    @Test
    void normalisesSwapInstId() {
        assertThat(OkxFundingRateProvider.normaliseSwapInstId("BTC-USDT")).isEqualTo("BTC-USDT-SWAP");
        assertThat(OkxFundingRateProvider.normaliseSwapInstId("BTCUSDT")).isEqualTo("BTC-USDT-SWAP");
        assertThat(OkxFundingRateProvider.normaliseSwapInstId("BTC-USDT-SWAP")).isEqualTo("BTC-USDT-SWAP");
        assertThat(OkxFundingRateProvider.normaliseSwapInstId("eth/usdt")).isEqualTo("ETH-USDT-SWAP");
    }

    @Test
    void parsesPayloadAndKeysByFundingTime() {
        nextResponse.set("""
                {"code":"0","data":[
                  {"instId":"BTC-USDT-SWAP","fundingTime":"1700006400000","fundingRate":"0.0001","realizedRate":"0.00012"},
                  {"instId":"BTC-USDT-SWAP","fundingTime":"1699920000000","fundingRate":"-0.0002","realizedRate":""},
                  {"instId":"BTC-USDT-SWAP","fundingTime":"1699833600000","fundingRate":"0.0003","realizedRate":"0.0003"}
                ]}
                """);
        var provider = new OkxFundingRateProvider("BTC-USDT-SWAP", 5,
                new RedirectingHttpClient(server.getAddress()));
        NavigableMap<Instant, BigDecimal> map = provider.load();
        assertThat(map).hasSize(3);

        // Prefers realizedRate when present, falls back to fundingRate when blank
        Instant t1 = Instant.ofEpochMilli(1700006400000L);
        Instant t2 = Instant.ofEpochMilli(1699920000000L);
        assertThat(map.get(t1)).isEqualByComparingTo("0.00012");
        assertThat(map.get(t2)).isEqualByComparingTo("-0.0002");

        // floorEntry gives the most recent rate at or before any moment
        Instant between = Instant.ofEpochMilli(1699920000000L + 1000);
        assertThat(map.floorEntry(between).getValue()).isEqualByComparingTo("-0.0002");
    }

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
            URI rewritten = URI.create("http://" + targetServer.getHostString() + ":" + targetServer.getPort()
                    + u.getRawPath()
                    + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery()));
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
