package com.klinekit.engine;

import com.klinekit.domain.Side;
import com.klinekit.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTest {

    @Test
    void buyDeductsCashAndAddsPosition() {
        Portfolio p = new Portfolio(new BigDecimal("10000"));
        Trade t = new Trade("o1", "BTCUSDT", Side.BUY,
                new BigDecimal("0.1"), new BigDecimal("50000"),
                new BigDecimal("5"), Instant.EPOCH);
        p.apply(t);

        // cash: 10000 - 0.1*50000 - 5 = 4995
        assertThat(p.cash()).isEqualByComparingTo("4995");
        assertThat(p.position("BTCUSDT").quantity()).isEqualByComparingTo("0.1");
        assertThat(p.position("BTCUSDT").avgCost()).isEqualByComparingTo("50000");
    }

    @Test
    void sellAddsCashAndRemovesFlatPosition() {
        Portfolio p = new Portfolio(new BigDecimal("0"));
        p.apply(new Trade("o1", "BTC", Side.BUY,
                new BigDecimal("1"), new BigDecimal("100"),
                BigDecimal.ZERO, Instant.EPOCH));
        p.apply(new Trade("o2", "BTC", Side.SELL,
                new BigDecimal("1"), new BigDecimal("150"),
                BigDecimal.ZERO, Instant.EPOCH));

        assertThat(p.cash()).isEqualByComparingTo("50");
        assertThat(p.positions()).doesNotContainKey("BTC");
    }

    @Test
    void equityAddsPositionMarkToCash() {
        Portfolio p = new Portfolio(new BigDecimal("1000"));
        p.apply(new Trade("o", "BTC", Side.BUY,
                new BigDecimal("1"), new BigDecimal("100"),
                BigDecimal.ZERO, Instant.EPOCH));
        assertThat(p.equity("BTC", new BigDecimal("250"))).isEqualByComparingTo("1150");
    }
}
