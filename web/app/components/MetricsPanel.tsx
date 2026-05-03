"use client";

import type { RunSummary } from "../lib/api";

type Props = {
  run: RunSummary;
  buyHoldPct: number | null;
  swatch: string;
};

export function MetricsPanel({ run, buyHoldPct, swatch }: Props) {
  const m = run.metrics;
  const ret = num(m.totalReturnPct);
  const dd = num(m.maxDrawdownPct);
  const sharpe = num(m.sharpe);
  const win = num(m.winRatePct);
  const trades = num(m.tradeCount);

  return (
    <div className="rounded-xl border border-zinc-800 bg-zinc-950/50 p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="h-3 w-3 rounded-full" style={{ background: swatch }} />
        <h3 className="font-mono text-sm">{run.strategy}</h3>
        <span className="ml-auto text-xs text-zinc-500">
          {run.symbol} · {run.tradeCount} trades
        </span>
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <Stat label="Total return" value={pct(ret)} good={ret !== null && ret > 0} bad={ret !== null && ret < 0} />
        <Stat label="vs buy & hold" value={buyHoldPct === null || ret === null ? "—" : pct(ret - buyHoldPct)} good={buyHoldPct !== null && ret !== null && ret > buyHoldPct} />
        <Stat label="Max drawdown" value={pct(dd)} bad={dd !== null && dd > 0} />
        <Stat label="Sharpe" value={sharpe?.toFixed(2) ?? "—"} />
        <Stat label="Win rate" value={pct(win)} />
        <Stat label="Trades" value={trades?.toString() ?? "0"} />
      </div>
      <div className="mt-3 pt-3 border-t border-zinc-800 text-xs text-zinc-500">
        Final equity: <span className="text-zinc-200">${num(run.finalEquity)?.toFixed(2)}</span>
        <span className="mx-2">·</span>
        Initial: <span className="text-zinc-200">${num(run.initialCash)?.toFixed(2)}</span>
      </div>
    </div>
  );
}

function Stat({ label, value, good, bad }: { label: string; value: string; good?: boolean; bad?: boolean }) {
  const color = good ? "text-emerald-400" : bad ? "text-rose-400" : "text-zinc-200";
  return (
    <div className="flex justify-between gap-2">
      <span className="text-zinc-500">{label}</span>
      <span className={`font-mono ${color}`}>{value}</span>
    </div>
  );
}

function num(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

function pct(v: number | null): string {
  if (v === null) return "—";
  return `${v.toFixed(2)}%`;
}
