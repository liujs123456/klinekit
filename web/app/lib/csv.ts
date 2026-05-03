import type { CandleInput } from "./api";

const TS_KEYS = ["timestamp", "date", "datetime", "time", "unix", "open_time"];

export function parseCsvCandles(raw: string, defaultSymbol = "BTCUSDT"): CandleInput[] {
  const lines = raw.split(/\r?\n/).filter((l) => l.trim().length > 0);
  // Drop comment / URL banner lines (e.g. cryptodatadownload header)
  while (lines.length && (lines[0].startsWith("http://") || lines[0].startsWith("https://") || lines[0].startsWith("#"))) {
    lines.shift();
  }
  if (lines.length < 2) throw new Error("CSV needs at least header + one data row");

  const header = lines[0].split(",").map((c) => c.trim().toLowerCase());
  const idx = (name: string) => header.indexOf(name);

  const tsCol = TS_KEYS.map(idx).find((i) => i >= 0);
  const open = idx("open");
  const high = idx("high");
  const low = idx("low");
  const close = idx("close");
  const volume = idx("volume");
  if (tsCol === undefined || open < 0 || high < 0 || low < 0 || close < 0) {
    throw new Error(
      `CSV missing required columns. Need timestamp/date + open + high + low + close. Got: ${header.join(", ")}`,
    );
  }

  const out: CandleInput[] = [];
  for (let i = 1; i < lines.length; i++) {
    const cols = lines[i].split(",");
    const ts = parseTs(cols[tsCol]);
    if (!ts) continue;
    const c: CandleInput = {
      timestamp: ts,
      open: Number(cols[open]),
      high: Number(cols[high]),
      low: Number(cols[low]),
      close: Number(cols[close]),
      volume: volume >= 0 ? Number(cols[volume]) : 0,
    };
    if ([c.open, c.high, c.low, c.close].some((v) => !Number.isFinite(v))) continue;
    out.push(c);
  }
  out.sort((a, b) => a.timestamp.localeCompare(b.timestamp));
  if (out.length === 0) throw new Error("No valid rows parsed");
  return out;
}

function parseTs(raw: string): string | null {
  const v = raw?.trim();
  if (!v) return null;
  const asNum = Number(v);
  if (Number.isFinite(asNum)) {
    const ms = asNum > 4_000_000_000 ? asNum : asNum * 1000;
    const d = new Date(ms);
    if (!Number.isNaN(d.getTime())) return d.toISOString();
  }
  // ISO / yyyy-mm-dd / yyyy-mm-dd hh:mm:ss
  const trial = new Date(v.includes("T") ? v : v.replace(" ", "T") + (v.length === 10 ? "T00:00:00Z" : "Z"));
  if (!Number.isNaN(trial.getTime())) return trial.toISOString();
  return null;
}
