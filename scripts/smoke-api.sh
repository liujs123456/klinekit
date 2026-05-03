#!/usr/bin/env bash
# Smoke-tests a running klinekit API by submitting a backtest and reading the result.
# Usage:
#   API=http://localhost:8080 ./scripts/smoke-api.sh

set -euo pipefail

API="${API:-http://localhost:8080}"

echo "→ POST $API/api/v1/backtest (5 candles, dca strategy)"
RESPONSE="$(curl -fsS -X POST "$API/api/v1/backtest" \
  -H "content-type: application/json" \
  -d '{
    "strategy": "dca",
    "symbol": "BTCUSDT",
    "initialCash": "1000",
    "feeBps": "0",
    "slippageBps": "0",
    "params": {"usdPerBuy": "100", "intervalDays": "1"},
    "candles": [
      {"timestamp": "2024-01-01T00:00:00Z", "open": 100, "high": 100, "low": 100, "close": 100, "volume": 0},
      {"timestamp": "2024-01-02T00:00:00Z", "open": 110, "high": 110, "low": 110, "close": 110, "volume": 0},
      {"timestamp": "2024-01-03T00:00:00Z", "open": 120, "high": 120, "low": 120, "close": 120, "volume": 0},
      {"timestamp": "2024-01-04T00:00:00Z", "open": 130, "high": 130, "low": 130, "close": 130, "volume": 0},
      {"timestamp": "2024-01-05T00:00:00Z", "open": 140, "high": 140, "low": 140, "close": 140, "volume": 0}
    ]
  }')"

echo "$RESPONSE"
RUN_ID="$(printf '%s' "$RESPONSE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
echo
echo "→ GET $API/api/v1/runs/$RUN_ID/equity-curve"
curl -fsS "$API/api/v1/runs/$RUN_ID/equity-curve"
echo
echo "→ GET $API/api/v1/runs"
curl -fsS "$API/api/v1/runs" | head -c 500
echo
