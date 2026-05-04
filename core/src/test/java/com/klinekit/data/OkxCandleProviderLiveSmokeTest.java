package com.klinekit.data;

import com.klinekit.domain.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OkxCandleProviderLiveSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "KLINEKIT_LIVE_OKX", matches = "1")
    void fetchesActualBtcHistory() {
        List<Candle> out = new OkxCandleProvider("BTC-USDT", "1D", 5).load();
        assertThat(out).hasSize(5);
        assertThat(out.getFirst().timestamp()).isBefore(out.getLast().timestamp());
        assertThat(out.getFirst().symbol()).isEqualTo("BTCUSDT");
        assertThat(out).allSatisfy(c -> assertThat(c.close().signum()).isPositive());
    }
}
