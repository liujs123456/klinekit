package com.klinekit.cli;

import com.klinekit.data.CsvCandleProvider;
import com.klinekit.domain.BacktestResult;
import com.klinekit.domain.Candle;
import com.klinekit.engine.BacktestEngine;
import com.klinekit.strategy.Strategy;
import com.klinekit.strategy.spot.Dca;
import com.klinekit.strategy.spot.DipLadder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "backtest",
        description = "Run a backtest against a CSV of historical candles."
)
public final class BacktestCommand implements Callable<Integer> {

    @Option(names = {"-s", "--strategy"}, required = true,
            description = "Strategy to run. Supported: dca")
    String strategy;

    @Option(names = {"-c", "--csv"}, required = true,
            description = "Path to candle CSV (columns: date/timestamp, open, high, low, close, volume).")
    Path csv;

    @Option(names = {"--symbol"}, defaultValue = "BTCUSDT",
            description = "Symbol to associate candles with if CSV does not include one (default: ${DEFAULT-VALUE}).")
    String symbol;

    @Option(names = {"--cash"}, defaultValue = "10000",
            description = "Initial cash in USD (default: ${DEFAULT-VALUE}).")
    BigDecimal initialCash;

    @Option(names = {"--fee-bps"}, defaultValue = "10",
            description = "Per-trade fee in basis points (default: ${DEFAULT-VALUE} = 0.10%).")
    BigDecimal feeBps;

    @Option(names = {"--slippage-bps"}, defaultValue = "5",
            description = "Slippage in basis points applied to mid (default: ${DEFAULT-VALUE} = 0.05%).")
    BigDecimal slippageBps;

    @Option(names = {"--interval"}, defaultValue = "7",
            description = "[dca] Days between buys (default: ${DEFAULT-VALUE}).")
    int intervalDays;

    @Option(names = {"--usd"}, defaultValue = "100",
            description = "[dca] USD per buy (default: ${DEFAULT-VALUE}).")
    BigDecimal usdPerBuy;

    @Option(names = {"--ref-lookback"}, defaultValue = "30",
            description = "[dip-ladder] Days of rolling high used as reference price (default: ${DEFAULT-VALUE}).")
    int refLookbackDays;

    @Override
    public Integer call() {
        List<Candle> candles = new CsvCandleProvider(csv, symbol).load();
        if (candles.isEmpty()) {
            System.err.println("No candles loaded from " + csv);
            return 1;
        }

        Strategy s = buildStrategy(candles.getFirst().symbol());
        if (s == null) {
            System.err.println("Unknown strategy: " + strategy + " (supported: dca)");
            return 2;
        }

        BacktestEngine.Config config = new BacktestEngine.Config(initialCash, feeBps, slippageBps);
        BacktestResult result = new BacktestEngine(config).run(s, candles);

        printReport(result, candles);
        return 0;
    }

    private Strategy buildStrategy(String csvSymbol) {
        return switch (strategy.toLowerCase(Locale.ROOT)) {
            case "dca", "spot.dca" -> new Dca(csvSymbol, usdPerBuy, intervalDays);
            case "dip-ladder", "spot.dip-ladder" ->
                    new DipLadder(csvSymbol, DipLadder.DEFAULT_TIERS, refLookbackDays);
            default -> null;
        };
    }

    private void printReport(BacktestResult r, List<Candle> candles) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        BigDecimal first = candles.getFirst().close();
        BigDecimal last = candles.getLast().close();
        BigDecimal buyHoldPct = last.subtract(first)
                .divide(first, java.math.MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, java.math.RoundingMode.HALF_UP);

        System.out.println();
        System.out.println("=== klinekit backtest ===");
        System.out.println("strategy:      " + r.strategy());
        System.out.println("symbol:        " + r.symbol());
        System.out.println("range:         " + fmt.format(r.start()) + " → " + fmt.format(r.end()));
        System.out.println("candles:       " + candles.size());
        System.out.println("initialCash:   $" + r.initialCash().toPlainString());
        System.out.println("finalEquity:   $" + r.finalEquity().setScale(2, java.math.RoundingMode.HALF_UP));
        System.out.println("buyHoldReturn: " + buyHoldPct + "%   (compare baseline)");
        System.out.println("trades:        " + r.trades().size());
        System.out.println();
        System.out.println("metrics:");
        for (Map.Entry<String, BigDecimal> e : r.metrics().entrySet()) {
            System.out.printf("  %-18s %s%n", e.getKey(), e.getValue().toPlainString());
        }
        System.out.println();
    }
}
