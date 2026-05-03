package com.klinekit.data;

import com.klinekit.domain.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCandleProviderTest {

    @Test
    void parsesIsoDateHeaderRow(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("d.csv");
        Files.writeString(csv, """
                date,open,high,low,close,volume
                2024-01-01,100,110,90,105,1000
                2024-01-02,105,115,100,112,1100
                """);

        List<Candle> out = new CsvCandleProvider(csv, "BTCUSDT").load();
        assertThat(out).hasSize(2);
        assertThat(out.getFirst().symbol()).isEqualTo("BTCUSDT");
        assertThat(out.getFirst().close()).isEqualByComparingTo("105");
        assertThat(out.get(1).close()).isEqualByComparingTo("112");
    }

    @Test
    void parsesUnixSecondsAndSorts(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("u.csv");
        // unsorted intentionally
        Files.writeString(csv, """
                unix,open,high,low,close,volume,symbol
                1704153600,105,115,100,112,1100,BTC/USDT
                1704067200,100,110,90,105,1000,BTC/USDT
                """);

        List<Candle> out = new CsvCandleProvider(csv, "FALLBACK").load();
        assertThat(out).hasSize(2);
        assertThat(out.getFirst().close()).isEqualByComparingTo("105");
        assertThat(out.get(1).close()).isEqualByComparingTo("112");
        assertThat(out.getFirst().symbol()).isEqualTo("BTCUSDT");
    }

    @Test
    void skipsLeadingCommentAndUrlLines(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("cdd.csv");
        Files.writeString(csv, """
                https://www.cryptodatadownload.com
                date,open,high,low,close,volume
                2024-01-01,100,110,90,105,1000
                """);

        List<Candle> out = new CsvCandleProvider(csv, "BTCUSDT").load();
        assertThat(out).hasSize(1);
    }
}
