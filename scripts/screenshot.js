// Headless screenshot of the klinekit dashboard with three overlaid runs.
//
// Assumes:
//   - the API is running on http://localhost:8080 (use ./scripts/dev.sh)
//   - the dashboard is running on http://localhost:3000
//
// Outputs: docs/dashboard.png at ~1600x1000.
//
// Usage:
//   ./scripts/dev.sh &       # in another terminal
//   node scripts/screenshot.js

const { chromium } = require("playwright");

const API = process.env.API_URL || "http://localhost:8080/api/v1";
const WEB = process.env.WEB_URL || "http://localhost:3010";
const OUT = "docs/dashboard.png";

const RUNS = [
  {
    label: "spot.dca",
    body: {
      strategy: "dca", symbol: "BTC-USDT",
      initialCash: "10000", feeBps: "10", slippageBps: "5",
      params: { usdPerBuy: "100", intervalDays: "7" },
      source: { provider: "okx", symbol: "BTC-USDT", bar: "1D", count: 365 },
    },
  },
  {
    label: "spot.dip-ladder",
    body: {
      strategy: "dip-ladder", symbol: "BTC-USDT",
      initialCash: "10000", feeBps: "10", slippageBps: "5",
      params: { refLookbackDays: "30" },
      source: { provider: "okx", symbol: "BTC-USDT", bar: "1D", count: 365 },
    },
  },
  {
    label: "perp.dca-martingale",
    body: {
      strategy: "perp.dca-martingale", symbol: "BTC-USDT",
      initialCash: "10000", feeBps: "10", slippageBps: "5",
      params: {
        direction: "LONG", leverage: "5", baseQty: "0.005",
        pullbackPct: "0.02", takeProfitPct: "0.01",
        multiplier: "2", maxOrders: "6",
        fundingRatePer8h: "0.0001",
        stopLossPct: "0.6",
      },
      source: { provider: "okx", symbol: "BTC-USDT", bar: "4H", count: 540 },
    },
  },
];

async function postBacktest(body) {
  const res = await fetch(`${API}/backtest`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST /backtest -> ${res.status}: ${await res.text()}`);
  return res.json();
}

async function getRun(id, path) {
  const res = await fetch(`${API}/runs/${id}/${path}`);
  if (!res.ok) throw new Error(`GET /runs/${id}/${path} -> ${res.status}`);
  return res.json();
}

(async () => {
  console.log("Submitting backtests via API …");
  const seeded = [];
  for (const r of RUNS) {
    process.stdout.write(`  ${r.label.padEnd(22)}`);
    const t0 = Date.now();
    const summary = await postBacktest(r.body);
    const [curve, trades] = await Promise.all([
      getRun(summary.id, "equity-curve"),
      getRun(summary.id, "trades"),
    ]);
    seeded.push({ id: summary.id, label: r.label, summary, curve, trades });
    console.log(` ✔ (${Date.now() - t0} ms, ${curve.length} pts)`);
  }

  console.log("Launching headless browser …");
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();

  // Inject the runs into client state by interacting with the form is heavy.
  // Instead we let the page's own code call listRuns() (already wired in the
  // dashboard), and use a helper bookmark to push the equity curves directly.
  await page.goto(WEB, { waitUntil: "domcontentloaded" });
  // Soft-wait for the H1; tolerate Next.js dev-mode first-paint delay.
  await page.waitForSelector("h1", { timeout: 60000 });
  await page.waitForLoadState("networkidle").catch(() => {});

  // Drive the form three times to overlay the runs we already seeded.
  // The dashboard's form posts to the API itself; reusing fetch-based code path.
  const presets = [
    { strategy: "dip-ladder", strategyLabel: "Spot · Dip ladder" },
    { strategy: "dca", strategyLabel: "Spot · DCA" },
    { strategy: "perp.dca-martingale", strategyLabel: "Perp · DCA-Martingale" },
  ];

  for (const preset of presets) {
    try {
      // Pick the strategy in the dropdown by visible label.
      await page.selectOption("select", { label: presetLabel(preset) }).catch(async () => {
        // Fallback: select by value (matches our STRATEGIES ids).
        await page.selectOption("select", preset.strategy);
      });
      await page.click('button:has-text("Fetch")');
      // Wait until a chart line appears (Recharts <path class="recharts-line-curve">).
      await page.waitForSelector(".recharts-line-curve", { timeout: 60000 });
      await page.waitForTimeout(800);
    } catch (e) {
      console.log(`  skip preset ${preset.strategy}: ${e.message.split("\n")[0]}`);
    }
  }

  // Give recharts time to layout if any animations remain.
  await page.waitForTimeout(800);
  await page.screenshot({ path: OUT, fullPage: false });
  console.log(`Wrote ${OUT}`);
  await browser.close();
})();

function presetLabel(p) {
  if (p.strategy === "dca") return "Spot · DCA — buy fixed USD every N days";
  if (p.strategy === "dip-ladder") return "Spot · Dip ladder — 4 tiers from rolling high";
  if (p.strategy === "perp.grid") return "Perp · Grid — bidirectional level-based DCA + TP";
  if (p.strategy === "perp.dca-martingale") return "Perp · DCA-Martingale — double-down + take profit";
  return p.strategyLabel;
}
