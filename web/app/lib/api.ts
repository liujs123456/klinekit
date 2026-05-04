export const API_BASE =
  process.env.NEXT_PUBLIC_KLINEKIT_API ?? "http://localhost:8080/api/v1";

export type CandleInput = {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
};

export type DataSourceSpec = {
  provider: "okx";
  symbol?: string;
  bar?: string;
  count?: number;
};

export type BacktestRequest = {
  strategy: string;
  symbol: string;
  initialCash: string;
  feeBps: string;
  slippageBps: string;
  params: Record<string, string | number>;
  candles?: CandleInput[];
  source?: DataSourceSpec;
};

export type RunSummary = {
  id: string;
  strategy: string;
  symbol: string;
  startAt: string;
  endAt: string;
  initialCash: string;
  finalEquity: string;
  config: Record<string, unknown>;
  metrics: Record<string, number | string>;
  createdAt: string;
  tradeCount: number;
  equityPointCount: number;
};

export type EquityPoint = { seq: number; timestamp: string; equity: string };

export type Trade = {
  seq: number;
  orderId: string;
  symbol: string;
  side: "BUY" | "SELL";
  quantity: string;
  price: string;
  fee: string;
  executedAt: string;
};

async function jfetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "content-type": "application/json",
      ...(init?.headers ?? {}),
    },
    cache: "no-store",
  });
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body?.message) msg = `${msg} — ${body.message}`;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}

export const api = {
  runBacktest: (req: BacktestRequest) =>
    jfetch<RunSummary>("/backtest", { method: "POST", body: JSON.stringify(req) }),

  listRuns: () => jfetch<RunSummary[]>("/runs"),

  getRun: (id: string) => jfetch<RunSummary>(`/runs/${id}`),

  getEquityCurve: (id: string) => jfetch<EquityPoint[]>(`/runs/${id}/equity-curve`),

  getTrades: (id: string) => jfetch<Trade[]>(`/runs/${id}/trades`),
};
