"use client";

import { useEffect, useMemo, useState } from "react";
import { api, type BacktestRequest, type CandleInput, type EquityPoint, type RunSummary, type Trade } from "./lib/api";
import { BacktestForm } from "./components/BacktestForm";
import { EquityChart } from "./components/EquityChart";
import { MetricsPanel } from "./components/MetricsPanel";

const COLORS = ["#34d399", "#60a5fa", "#f472b6", "#facc15", "#a78bfa"];

type RunWithCurve = {
  run: RunSummary;
  curve: EquityPoint[];
  trades: Trade[];
  color: string;
};

export default function Home() {
  const [candles, setCandles] = useState<CandleInput[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<RunWithCurve[]>([]);
  const [recent, setRecent] = useState<RunSummary[]>([]);

  useEffect(() => {
    api.listRuns().then(setRecent).catch(() => undefined);
  }, []);

  async function runBacktest(req: BacktestRequest) {
    setBusy(true);
    setError(null);
    try {
      const summary = await api.runBacktest(req);
      const [curve, trades] = await Promise.all([
        api.getEquityCurve(summary.id),
        api.getTrades(summary.id),
      ]);
      setResults((rs) => [
        ...rs,
        { run: summary, curve, trades, color: COLORS[rs.length % COLORS.length] },
      ]);
      const refreshed = await api.listRuns();
      setRecent(refreshed);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function liquidationDots(curve: EquityPoint[], trades: Trade[]) {
    const eqByTs = new Map(curve.map((p) => [p.timestamp, Number(p.equity)]));
    return trades
      .filter((t) => t.orderId.startsWith("LIQ-"))
      .map((t) => ({ timestamp: t.executedAt, equity: eqByTs.get(t.executedAt) ?? 0 }))
      .filter((d) => d.equity > 0);
  }

  const buyHoldPct = useMemo(() => {
    if (results.length === 0) {
      if (!candles || candles.length < 2) return null;
      const first = candles[0].close;
      const last = candles[candles.length - 1].close;
      if (!first) return null;
      return ((last - first) / first) * 100;
    }
    return null;
  }, [candles, results]);

  function clearResults() {
    setResults([]);
  }

  return (
    <main className="min-h-screen bg-zinc-950 text-zinc-100">
      <header className="border-b border-zinc-900 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-baseline gap-3">
          <h1 className="text-xl font-bold tracking-tight">klinekit</h1>
          <span className="text-zinc-500 text-sm">Java backtest engine for crypto strategies</span>
          <a
            href="https://github.com/liujs123456/klinekit"
            target="_blank"
            rel="noreferrer"
            className="ml-auto text-xs text-zinc-500 hover:text-zinc-200"
          >
            github.com/liujs123456/klinekit ↗
          </a>
        </div>
      </header>

      <div className="max-w-7xl mx-auto p-6 grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-6">
        <aside className="space-y-4">
          <BacktestForm
            candles={candles}
            onCandles={(cs) => setCandles(cs)}
            onSubmit={runBacktest}
            busy={busy}
          />
          {error && (
            <div className="rounded-md border border-rose-500/40 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
              {error}
            </div>
          )}
          {recent.length > 0 && (
            <div className="rounded-xl border border-zinc-800 bg-zinc-950/50 p-3">
              <h3 className="text-xs uppercase tracking-wider text-zinc-500 mb-2">Recent runs</h3>
              <ul className="space-y-1.5 text-xs">
                {recent.slice(0, 8).map((r) => (
                  <li key={r.id} className="flex justify-between gap-2 text-zinc-300">
                    <span className="font-mono truncate">{r.strategy}</span>
                    <span className="text-zinc-500 shrink-0">{r.symbol}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>

        <section className="space-y-4">
          <div className="rounded-xl border border-zinc-800 bg-zinc-950/50 p-4">
            <div className="flex items-center justify-between mb-3">
              <div>
                <h2 className="font-semibold">Equity curve</h2>
                <p className="text-xs text-zinc-500">
                  {results.length === 0
                    ? "Submit a backtest to overlay strategies."
                    : `${results.length} run${results.length === 1 ? "" : "s"} on chart${
                        buyHoldPct !== null ? ` · buy-hold baseline: ${buyHoldPct.toFixed(2)}%` : ""
                      }`}
                </p>
              </div>
              {results.length > 0 && (
                <button
                  onClick={clearResults}
                  className="text-xs text-zinc-500 hover:text-zinc-200"
                >
                  Clear
                </button>
              )}
            </div>
            <EquityChart
              series={results.map((r) => ({
                id: r.run.id,
                label: `${r.run.strategy}`,
                color: r.color,
                points: r.curve,
                liquidations: liquidationDots(r.curve, r.trades),
              }))}
            />
            {results.some((r) => r.trades.some((t) => t.orderId.startsWith("LIQ-"))) && (
              <p className="mt-2 text-xs text-rose-400/80">
                ● red dots mark liquidation events (perp position auto-closed at liq price)
              </p>
            )}
          </div>

          {results.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {results.map((r) => (
                <MetricsPanel key={r.run.id} run={r.run} buyHoldPct={buyHoldPct} swatch={r.color} />
              ))}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
