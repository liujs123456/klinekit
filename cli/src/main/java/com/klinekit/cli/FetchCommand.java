package com.klinekit.cli;

import com.klinekit.data.OkxCandleProvider;
import com.klinekit.domain.Candle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "fetch",
        description = "Fetch historical candles from OKX and write a klinekit-compatible CSV."
)
public final class FetchCommand implements Callable<Integer> {

    @Option(names = {"--symbol"}, defaultValue = "BTC-USDT",
            description = "Instrument id, e.g. BTC-USDT or BTCUSDT (default: ${DEFAULT-VALUE}).")
    String symbol;

    @Option(names = {"--bar"}, defaultValue = "1D",
            description = "Bar size — 1m / 15m / 1H / 4H / 1D / 1W (default: ${DEFAULT-VALUE}).")
    String bar;

    @Option(names = {"--count"}, defaultValue = "365",
            description = "Number of candles to fetch (default: ${DEFAULT-VALUE}).")
    int count;

    @Option(names = {"--out"}, required = true,
            description = "Output CSV path.")
    Path out;

    @Override
    public Integer call() throws IOException {
        List<Candle> candles = new OkxCandleProvider(symbol, bar, count).load();
        if (candles.isEmpty()) {
            System.err.println("No candles fetched.");
            return 1;
        }
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("timestamp,open,high,low,close,volume,symbol\n");
            for (Candle c : candles) {
                w.write(c.timestamp().toString());
                w.write(",");
                w.write(c.open().toPlainString());
                w.write(",");
                w.write(c.high().toPlainString());
                w.write(",");
                w.write(c.low().toPlainString());
                w.write(",");
                w.write(c.close().toPlainString());
                w.write(",");
                w.write(c.volume().toPlainString());
                w.write(",");
                w.write(c.symbol());
                w.write("\n");
            }
        }
        System.out.println("Wrote " + candles.size() + " candles to " + out + " (" + candles.getFirst().timestamp() + " → " + candles.getLast().timestamp() + ")");
        return 0;
    }
}
