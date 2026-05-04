"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Legend,
} from "recharts";
import type { EquityPoint } from "../lib/api";

type Series = {
  id: string;
  label: string;
  color: string;
  points: EquityPoint[];
  liquidations?: { timestamp: string; equity: number }[];
};

type Baseline = {
  id: string;
  label: string;
  points: EquityPoint[];
};

type Props = {
  series: Series[];
  baseline?: Baseline;
};

export function EquityChart({ series, baseline }: Props) {
  const allSeries = baseline && baseline.points.length > 0
      ? [...series, { id: baseline.id, label: baseline.label, color: "#52525b", points: baseline.points } as Series]
      : series;
  const merged = mergeSeries(allSeries);
  if (!merged.length) {
    return (
      <div className="h-[360px] flex items-center justify-center text-zinc-500">
        Run a backtest to see the equity curve.
      </div>
    );
  }
  return (
    <div className="h-[360px]">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={merged} margin={{ top: 10, right: 24, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
          <XAxis
            dataKey="ts"
            tickFormatter={(v: string) => v.slice(0, 10)}
            stroke="rgba(255,255,255,0.4)"
            tick={{ fontSize: 11 }}
            minTickGap={48}
          />
          <YAxis
            stroke="rgba(255,255,255,0.4)"
            tick={{ fontSize: 11 }}
            tickFormatter={(v: number) => "$" + v.toLocaleString(undefined, { maximumFractionDigits: 0 })}
            width={70}
          />
          <Tooltip
            contentStyle={{ background: "#0a0a0a", border: "1px solid #27272a", borderRadius: 8 }}
            labelFormatter={(v) => String(v ?? "").slice(0, 19).replace("T", " ")}
            formatter={(v) => "$" + Number(v).toFixed(2)}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {baseline && baseline.points.length > 0 && (
            <Line
              type="monotone"
              dataKey={baseline.id}
              name={baseline.label}
              stroke="#52525b"
              strokeDasharray="4 4"
              strokeWidth={1.5}
              dot={false}
              isAnimationActive={false}
            />
          )}
          {series.map((s) => (
            <Line
              key={s.id}
              type="monotone"
              dataKey={s.id}
              name={s.label}
              stroke={s.color}
              dot={false}
              strokeWidth={2}
              isAnimationActive={false}
            />
          ))}
          {series.flatMap((s) =>
            (s.liquidations ?? []).map((liq, i) => (
              <ReferenceDot
                key={`${s.id}-liq-${i}`}
                x={liq.timestamp}
                y={liq.equity}
                r={5}
                fill="#f43f5e"
                stroke="#fff"
                strokeWidth={1}
              />
            )),
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function mergeSeries(series: Series[]): Array<Record<string, number | string>> {
  const byTs = new Map<string, Record<string, number | string>>();
  for (const s of series) {
    for (const p of s.points) {
      const key = p.timestamp;
      const row = byTs.get(key) ?? { ts: key };
      row[s.id] = Number(p.equity);
      byTs.set(key, row);
    }
  }
  return [...byTs.values()].sort((a, b) => String(a.ts).localeCompare(String(b.ts)));
}
