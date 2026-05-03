# klinekit

Java backtest engine for crypto trading strategies — spot DCA today, perpetual contracts (leverage / liquidation / funding rate) on the way.

> **Status:** **M1 shipped** — core engine, spot DCA, dip-ladder, CLI, 22 unit tests.
> M2 (Spring Boot REST API + Postgres + Flyway) and M3 (perp support + Grid + DCA-Martingale) are queued.

## Why

- Most retail crypto traders run strategies live without ever back-testing them. klinekit makes it cheap to ask *"what would this have actually done?"* before wiring real capital.
- The core ships as a **standalone Spring-free jar** so it can be embedded in scripts, services, or notebooks. The (forthcoming) REST API is a thin layer on top.
- Designed in idiomatic **Java 21** — records, sealed types, switch expressions, pattern matching. No Lombok in core.

## Headline result — does the dip-ladder actually beat DCA?

The included [`spot.dip-ladder`](app/src/main/java/com/klinekit/strategy/spot/DipLadder.java) is a port of the live tier-ladder strategy from [`crypto-dca-monitor`](../btc-dca/crypto-dca-monitor) — buys $100/$200/$400/$800 at -10/-15/-22/-32% from a 30-day rolling high, with a 5% rebound re-arm.

Backtest over **2025-05 → 2026-05** (BTC: \$95,922 → \$78,717, **buy-hold -17.9%**), $10,000 initial cash:

| Strategy | Final equity | Total return | Max drawdown | Trades |
|---|---:|---:|---:|---:|
| Buy & hold | \$8,206 | **-17.94%** | n/a | 1 |
| `spot.dca` (weekly $200) | \$8,397 | -16.03% | 35.04% | 49 |
| **`spot.dip-ladder`** | **\$10,016** | **+0.16%** | **6.51%** | **11** |

The dip-ladder preserved capital in a -18% year by sitting out non-dip days and only firing on real drawdowns. DCA's flat cadence forced it to keep buying through a downtrend.

(Numbers are reproducible with `sample-data/btc-1y-daily.csv` and the commands in the [Quick start](#quick-start) below.)

## Quick start

```bash
# Build the fat jar (requires JDK 21 — Gradle will auto-provision via foojay)
./gradlew shadowJar

# Run a backtest
java -jar app/build/libs/klinekit-0.1.0.jar backtest \
    --strategy=dca \
    --csv=sample-data/btc-toy-1d.csv \
    --interval=7 \
    --usd=100 \
    --cash=10000
```

Sample output:

```
=== klinekit backtest ===
strategy:      spot.dca
symbol:        BTCUSDT
range:         2024-01-01T00:00:00Z → 2024-01-15T00:00:00Z
candles:       15
initialCash:   $5000
finalEquity:   $5225.41
buyHoldReturn: 17.9070%   (compare baseline)
trades:        5

metrics:
  totalReturnPct     4.5081
  sharpe             22.5741
  maxDrawdownPct     0.1707
  winRatePct         0
  tradeCount         5
```

`buyHoldReturn` is printed alongside as the always-relevant baseline — a strategy that underperforms buy-and-hold isn't worth running.

## CLI

```
klinekit backtest [OPTIONS]

  -s, --strategy=<strategy>      dca | dip-ladder (more in M3: grid, dca-martingale, ma-crossover)
  -c, --csv=<csv>                Path to candle CSV
      --symbol=<symbol>          Fallback symbol if CSV omits it (default: BTCUSDT)
      --cash=<initialCash>       Initial USD cash (default: 10000)
      --fee-bps=<feeBps>         Fee in basis points per trade (default: 10 = 0.10%)
      --slippage-bps=<bps>       Slippage in bps applied to mid (default: 5 = 0.05%)
      --interval=<intervalDays>  [dca] Days between buys (default: 7)
      --usd=<usdPerBuy>          [dca] USD per buy (default: 100)
      --ref-lookback=<days>      [dip-ladder] Days of rolling high used as reference (default: 30)
```

## CSV format

The provider sniffs columns case-insensitively and accepts:

- **Timestamp:** `date`, `datetime`, `timestamp`, `time`, `unix`, `open_time` — ISO-8601, plain dates, or epoch seconds/milliseconds
- **Required:** `open`, `high`, `low`, `close`
- **Optional:** `volume` (or `volume_btc` / `volume_usdt`), `symbol` / `pair`

Lines starting with `https://` (e.g., the cryptodatadownload.com header) are skipped automatically.

## Architecture

```
app/src/main/java/com/klinekit/
├── domain/         Candle, Order, Position, Trade, BacktestResult — records, BigDecimal money, Instant time
├── strategy/       Strategy interface + StrategyContext
│   └── spot/
│       ├── Dca         Buy fixed USD every N days
│       └── DipLadder   4-tier dip ladder, fires once per tier with rebound re-arm
├── engine/         BacktestEngine, Portfolio, OrderRouter, SimulatedOrderRouter (fee + slippage)
├── data/           CandleProvider interface + CsvCandleProvider
├── metrics/        Total return, Sharpe (annualised from observed candle spacing), Max drawdown, Win rate
└── cli/            picocli entrypoint (Main + BacktestCommand)
```

The engine is rebuildable in 5 lines:

```java
List<Candle> candles = new CsvCandleProvider(Path.of("btc-1d.csv"), "BTCUSDT").load();
Strategy strat = new Dca("BTCUSDT", new BigDecimal("100"), 7);
BacktestResult result = new BacktestEngine().run(strat, candles);
```

## Roadmap

| Milestone | Scope | Status |
|---|---|---|
| **M1** | Core engine + DCA spot + CLI + JUnit | ✅ shipped |
| **M2** | Spring Boot REST API + PostgreSQL + Flyway + Testcontainers + OpenAPI | queued |
| **M3** | Perp support (leverage / liquidation / funding rate) + Grid + DCA-Martingale + OKX history fetch | queued |

## Development

```bash
./gradlew test           # run all unit tests (JUnit 5 + AssertJ)
./gradlew run --args="backtest --strategy=dca --csv=$(pwd)/sample-data/btc-toy-1d.csv"
./gradlew shadowJar      # produce fat jar at app/build/libs/klinekit-<ver>.jar
```

## License

MIT — see [LICENSE](LICENSE).
