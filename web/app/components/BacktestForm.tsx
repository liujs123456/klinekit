"use client";

import { useState } from "react";
import type { BacktestRequest, CandleInput } from "../lib/api";
import { parseCsvCandles } from "../lib/csv";

type Props = {
  candles: CandleInput[] | null;
  onCandles: (cs: CandleInput[], filename: string) => void;
  onSubmit: (req: BacktestRequest) => Promise<void>;
  busy: boolean;
};

const STRATEGIES = [
  { id: "dca", label: "DCA — buy fixed USD every N days" },
  { id: "dip-ladder", label: "Dip ladder — 4 tiers from rolling high" },
];

export function BacktestForm({ candles, onCandles, onSubmit, busy }: Props) {
  const [strategy, setStrategy] = useState<string>("dca");
  const [symbol, setSymbol] = useState("BTCUSDT");
  const [initialCash, setInitialCash] = useState("10000");
  const [feeBps, setFeeBps] = useState("10");
  const [slippageBps, setSlippageBps] = useState("5");
  const [usdPerBuy, setUsdPerBuy] = useState("100");
  const [intervalDays, setIntervalDays] = useState("7");
  const [refLookback, setRefLookback] = useState("30");
  const [filename, setFilename] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    setError(null);
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const text = await file.text();
      const parsed = parseCsvCandles(text, symbol);
      onCandles(parsed, file.name);
      setFilename(file.name);
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function handleSubmit() {
    setError(null);
    if (!candles || candles.length === 0) {
      setError("Upload a CSV first.");
      return;
    }
    const params: Record<string, string | number> =
      strategy === "dca"
        ? { usdPerBuy, intervalDays: Number(intervalDays) }
        : { refLookbackDays: Number(refLookback) };
    try {
      await onSubmit({
        strategy,
        symbol,
        initialCash,
        feeBps,
        slippageBps,
        params,
        candles,
      });
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="rounded-xl border border-zinc-800 bg-zinc-950/50 p-4 space-y-4">
      <div>
        <label className="block text-xs text-zinc-500 mb-1">CSV (date,open,high,low,close,volume)</label>
        <input
          type="file"
          accept=".csv,text/csv"
          onChange={handleFile}
          className="block w-full text-sm file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border file:border-zinc-700 file:bg-zinc-900 file:text-zinc-100 hover:file:bg-zinc-800"
        />
        {filename && (
          <p className="mt-1 text-xs text-zinc-500">
            Loaded <span className="font-mono">{filename}</span> — {candles?.length ?? 0} candles.
          </p>
        )}
      </div>

      <div>
        <label className="block text-xs text-zinc-500 mb-1">Strategy</label>
        <select
          value={strategy}
          onChange={(e) => setStrategy(e.target.value)}
          className="w-full bg-zinc-900 border border-zinc-700 rounded-md px-2 py-1.5 text-sm"
        >
          {STRATEGIES.map((s) => (
            <option key={s.id} value={s.id}>{s.label}</option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <Field label="Symbol" value={symbol} onChange={setSymbol} />
        <Field label="Initial cash $" value={initialCash} onChange={setInitialCash} />
        <Field label="Fee bps" value={feeBps} onChange={setFeeBps} />
        <Field label="Slippage bps" value={slippageBps} onChange={setSlippageBps} />
      </div>

      {strategy === "dca" ? (
        <div className="grid grid-cols-2 gap-3">
          <Field label="USD per buy" value={usdPerBuy} onChange={setUsdPerBuy} />
          <Field label="Interval (days)" value={intervalDays} onChange={setIntervalDays} />
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <Field label="Ref lookback (days)" value={refLookback} onChange={setRefLookback} />
        </div>
      )}

      {error && <p className="text-rose-400 text-xs">{error}</p>}

      <button
        onClick={handleSubmit}
        disabled={busy || !candles}
        className="w-full bg-emerald-500 hover:bg-emerald-400 disabled:bg-zinc-700 disabled:text-zinc-500 text-zinc-950 font-medium py-2 rounded-md transition"
      >
        {busy ? "Running..." : "Run backtest"}
      </button>
    </div>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block">
      <span className="block text-xs text-zinc-500 mb-1">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full bg-zinc-900 border border-zinc-700 rounded-md px-2 py-1.5 text-sm font-mono"
      />
    </label>
  );
}
