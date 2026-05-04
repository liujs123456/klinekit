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
  { id: "dca", label: "Spot · DCA — buy fixed USD every N days" },
  { id: "dip-ladder", label: "Spot · Dip ladder — 4 tiers from rolling high" },
  { id: "perp.grid", label: "Perp · Grid — bidirectional level-based DCA + TP" },
  { id: "perp.dca-martingale", label: "Perp · DCA-Martingale — double-down + take profit" },
];

export function BacktestForm({ candles, onCandles, onSubmit, busy }: Props) {
  const [sourceMode, setSourceMode] = useState<"csv" | "okx">("okx");
  const [okxBar, setOkxBar] = useState("1D");
  const [okxCount, setOkxCount] = useState("365");

  const [strategy, setStrategy] = useState<string>("dip-ladder");
  const [symbol, setSymbol] = useState("BTC-USDT");
  const [initialCash, setInitialCash] = useState("10000");
  const [feeBps, setFeeBps] = useState("10");
  const [slippageBps, setSlippageBps] = useState("5");

  // spot.dca
  const [usdPerBuy, setUsdPerBuy] = useState("100");
  const [intervalDays, setIntervalDays] = useState("7");
  const [cashMode, setCashMode] = useState<"AUTO_INJECT" | "PHASED" | "LUMP">("AUTO_INJECT");
  const [phasedBudget, setPhasedBudget] = useState("10000");
  // spot.dip-ladder
  const [refLookback, setRefLookback] = useState("30");
  // perp.grid
  const [gridLower, setGridLower] = useState("70000");
  const [gridUpper, setGridUpper] = useState("100000");
  const [gridLevels, setGridLevels] = useState("8");
  const [gridLeverage, setGridLeverage] = useState("5");
  const [gridQty, setGridQty] = useState("0.01");
  // perp.dca-martingale
  const [dcamDirection, setDcamDirection] = useState<"LONG" | "SHORT">("LONG");
  const [dcamLeverage, setDcamLeverage] = useState("5");
  const [dcamBaseQty, setDcamBaseQty] = useState("0.005");
  const [dcamPullback, setDcamPullback] = useState("0.02");
  const [dcamTakeProfit, setDcamTakeProfit] = useState("0.01");
  const [dcamMultiplier, setDcamMultiplier] = useState("2");
  const [dcamMaxOrders, setDcamMaxOrders] = useState("6");
  // common: funding rate
  const [fundingRate, setFundingRate] = useState("0.0001");

  const [filename, setFilename] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  const isPerp = strategy.startsWith("perp.");

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

  function buildParams(): Record<string, string | number | boolean> {
    switch (strategy) {
      case "dca":
        if (cashMode === "PHASED") {
          return {
            cashMode,
            phasedBudget,
            intervalDays: Number(intervalDays),
          };
        }
        return {
          cashMode,
          usdPerBuy,
          intervalDays: Number(intervalDays),
        };
      case "dip-ladder":
        return { refLookbackDays: Number(refLookback) };
      case "perp.grid":
        return {
          lowerBound: gridLower,
          upperBound: gridUpper,
          levels: Number(gridLevels),
          leverage: gridLeverage,
          qtyPerLevel: gridQty,
          fundingRatePer8h: fundingRate,
        };
      case "perp.dca-martingale":
        return {
          direction: dcamDirection,
          leverage: dcamLeverage,
          baseQty: dcamBaseQty,
          pullbackPct: dcamPullback,
          takeProfitPct: dcamTakeProfit,
          multiplier: dcamMultiplier,
          maxOrders: Number(dcamMaxOrders),
          fundingRatePer8h: fundingRate,
        };
      default:
        return {};
    }
  }

  async function handleSubmit() {
    setError(null);
    const params = buildParams();
    const base = { strategy, symbol, initialCash, feeBps, slippageBps, params };
    let req: BacktestRequest;
    if (sourceMode === "okx") {
      const count = Number(okxCount);
      if (!Number.isFinite(count) || count <= 0 || count > 5000) {
        setError("Count must be between 1 and 5000.");
        return;
      }
      req = { ...base, source: { provider: "okx", symbol, bar: okxBar, count } };
    } else {
      if (!candles || candles.length === 0) {
        setError("Upload a CSV first.");
        return;
      }
      req = { ...base, candles };
    }

    try {
      await onSubmit(req);
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="rounded-xl border border-zinc-800 bg-zinc-950/50 p-4 space-y-4">
      <div>
        <label className="block text-xs text-zinc-500 mb-1">Data source</label>
        <div className="grid grid-cols-2 gap-1 p-1 bg-zinc-900 rounded-md border border-zinc-800">
          <button
            type="button"
            onClick={() => setSourceMode("okx")}
            className={`text-xs py-1.5 rounded transition ${sourceMode === "okx" ? "bg-zinc-800 text-zinc-100" : "text-zinc-500 hover:text-zinc-300"}`}
          >
            OKX history
          </button>
          <button
            type="button"
            onClick={() => setSourceMode("csv")}
            className={`text-xs py-1.5 rounded transition ${sourceMode === "csv" ? "bg-zinc-800 text-zinc-100" : "text-zinc-500 hover:text-zinc-300"}`}
          >
            CSV upload
          </button>
        </div>
      </div>

      {sourceMode === "okx" ? (
        <div className="grid grid-cols-2 gap-3">
          <Field label="Bar (1D / 4H / 1H ...)" value={okxBar} onChange={setOkxBar} />
          <Field label="Candle count" value={okxCount} onChange={setOkxCount} />
        </div>
      ) : (
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
      )}

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

      {strategy === "dca" && (
        <>
          <div>
            <label className="block text-xs text-zinc-500 mb-1">Cash mode</label>
            <div className="grid grid-cols-3 gap-1 p-1 bg-zinc-900 rounded-md border border-zinc-800">
              <ModeButton active={cashMode === "AUTO_INJECT"} onClick={() => setCashMode("AUTO_INJECT")}
                title="Recurring deposit DCA: each interval injects $X from outside (paycheck → buy BTC). Initial cash ignored.">
                Recurring
              </ModeButton>
              <ModeButton active={cashMode === "PHASED"} onClick={() => setCashMode("PHASED")}
                title="Phased entry: a fixed budget spread evenly across the entire backtest window. $/buy auto-computed.">
                Phased
              </ModeButton>
              <ModeButton active={cashMode === "LUMP"} onClick={() => setCashMode("LUMP")}
                title="Lump-sum-then-stop: spends down a fixed initial-cash pool $X per interval until empty.">
                Lump
              </ModeButton>
            </div>
            <p className="mt-1 text-xs text-zinc-500">
              {cashMode === "AUTO_INJECT" && "Each interval injects fresh USD — realistic DCA from your salary."}
              {cashMode === "PHASED" && "$/buy auto = budget / # of intervals. Spreads the same total over the entire window."}
              {cashMode === "LUMP" && "Spends initial cash $X/buy until the pool empties; then holds. Mostly for what-if analysis."}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            {cashMode === "PHASED" ? (
              <Field label="Total budget $" value={phasedBudget} onChange={setPhasedBudget} />
            ) : (
              <Field label="USD per buy" value={usdPerBuy} onChange={setUsdPerBuy} />
            )}
            <Field label="Interval (days)" value={intervalDays} onChange={setIntervalDays} />
          </div>
        </>
      )}

      {strategy === "dip-ladder" && (
        <div className="grid grid-cols-2 gap-3">
          <Field label="Ref lookback (days)" value={refLookback} onChange={setRefLookback} />
        </div>
      )}

      {strategy === "perp.grid" && (
        <>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Lower bound $" value={gridLower} onChange={setGridLower} />
            <Field label="Upper bound $" value={gridUpper} onChange={setGridUpper} />
            <Field label="Levels" value={gridLevels} onChange={setGridLevels} />
            <Field label="Leverage" value={gridLeverage} onChange={setGridLeverage} />
            <Field label="Qty per level" value={gridQty} onChange={setGridQty} />
            <Field label="Funding/8h" value={fundingRate} onChange={setFundingRate} />
          </div>
        </>
      )}

      {strategy === "perp.dca-martingale" && (
        <>
          <div>
            <label className="block text-xs text-zinc-500 mb-1">Direction</label>
            <div className="grid grid-cols-2 gap-1 p-1 bg-zinc-900 rounded-md border border-zinc-800">
              <button
                type="button"
                onClick={() => setDcamDirection("LONG")}
                className={`text-xs py-1.5 rounded transition ${dcamDirection === "LONG" ? "bg-emerald-500/20 text-emerald-300" : "text-zinc-500 hover:text-zinc-300"}`}
              >
                LONG
              </button>
              <button
                type="button"
                onClick={() => setDcamDirection("SHORT")}
                className={`text-xs py-1.5 rounded transition ${dcamDirection === "SHORT" ? "bg-rose-500/20 text-rose-300" : "text-zinc-500 hover:text-zinc-300"}`}
              >
                SHORT
              </button>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Leverage" value={dcamLeverage} onChange={setDcamLeverage} />
            <Field label="Base qty" value={dcamBaseQty} onChange={setDcamBaseQty} />
            <Field label="Pullback (e.g. 0.02 = 2%)" value={dcamPullback} onChange={setDcamPullback} />
            <Field label="Take profit (0.01 = 1%)" value={dcamTakeProfit} onChange={setDcamTakeProfit} />
            <Field label="Multiplier" value={dcamMultiplier} onChange={setDcamMultiplier} />
            <Field label="Max orders" value={dcamMaxOrders} onChange={setDcamMaxOrders} />
            <Field label="Funding/8h" value={fundingRate} onChange={setFundingRate} />
          </div>
        </>
      )}

      {isPerp && (
        <p className="text-xs text-amber-400/80 leading-relaxed">
          ⚠️ Perp orders use isolated-margin liquidation at <code className="font-mono">entry × (1 - 1/L + 0.5%)</code> for longs.
          Force-close on liq is automatic.
        </p>
      )}

      {error && <p className="text-rose-400 text-xs">{error}</p>}

      <button
        onClick={handleSubmit}
        disabled={busy || (sourceMode === "csv" && !candles)}
        className="w-full bg-emerald-500 hover:bg-emerald-400 disabled:bg-zinc-700 disabled:text-zinc-500 text-zinc-950 font-medium py-2 rounded-md transition"
      >
        {busy ? "Running..." : sourceMode === "okx" ? `Fetch ${okxCount} ${okxBar} bars + run` : "Run backtest"}
      </button>
    </div>
  );
}

function ModeButton({
  active, onClick, title, children,
}: { active: boolean; onClick: () => void; title: string; children: React.ReactNode }) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      className={`text-xs py-1.5 rounded transition ${active ? "bg-zinc-800 text-zinc-100" : "text-zinc-500 hover:text-zinc-300"}`}
    >
      {children}
    </button>
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
