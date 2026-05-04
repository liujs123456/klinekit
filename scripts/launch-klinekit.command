#!/usr/bin/env bash
# Double-click launcher for klinekit on macOS.
#
# Boots:
#   - Spring Boot API on :8080 (H2 in-memory, zero config)
#   - Next.js dashboard on :3000 (or :3010 if 3000 busy)
# Then opens the dashboard in your default browser.
#
# Quit: just close this terminal window (Ctrl+C also works).

set -euo pipefail

# Find the project root (repo dir) regardless of where the .command is launched from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Console banner.
clear
cat <<'BANNER'
==========================================================
  klinekit — crypto strategy backtester
  starting API + dashboard...
==========================================================
BANNER
echo "Project: $PROJECT_ROOT"
echo

# Pick web port — fall back to 3010 if 3000 is taken.
WEB_PORT=3000
if lsof -ti :3000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[i] port 3000 busy → using 3010 instead"
  WEB_PORT=3010
fi
API_PORT=8080
if lsof -ti :8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[!] port 8080 already serving — assuming klinekit API is running"
  ALREADY_API=1
else
  ALREADY_API=0
fi

# Cleanup on exit.
cleanup() {
  echo
  echo "[*] shutting down..."
  jobs -p | xargs -r kill 2>/dev/null || true
  pkill -f KlinekitApplication 2>/dev/null || true
  pkill -f "next dev.*$WEB_PORT" 2>/dev/null || true
  pkill -f "next-server.*$WEB_PORT" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Boot API (skip if user already has one running).
if [ "$ALREADY_API" -eq 0 ]; then
  echo "[1/3] starting Spring Boot API on :$API_PORT (H2)..."
  SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$API_PORT" \
    ./gradlew :api:bootRun --quiet > /tmp/klinekit-api.log 2>&1 &
  API_PID=$!

  echo -n "      waiting for API"
  for i in $(seq 1 60); do
    if curl -fsS "http://localhost:$API_PORT/v3/api-docs" >/dev/null 2>&1; then
      echo " ✅"
      break
    fi
    echo -n "."
    sleep 2
    if [ "$i" -eq 60 ]; then
      echo " ❌"
      echo "API failed to start. Last 30 log lines:"
      tail -30 /tmp/klinekit-api.log
      exit 1
    fi
  done
fi

# Install web deps if first run.
if [ ! -d web/node_modules ]; then
  echo "[2/3] first run — installing web dependencies (one minute)..."
  (cd web && npm install --silent)
fi

# Boot dashboard.
echo "[3/3] starting Next.js dashboard on :$WEB_PORT..."
(cd web && PORT="$WEB_PORT" \
  NEXT_PUBLIC_KLINEKIT_API="http://localhost:$API_PORT/api/v1" \
  npx --yes next dev --port "$WEB_PORT" > /tmp/klinekit-web.log 2>&1) &
WEB_PID=$!

echo -n "      waiting for dashboard"
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:$WEB_PORT" >/dev/null 2>&1; then
    echo " ✅"
    break
  fi
  echo -n "."
  sleep 2
  if [ "$i" -eq 60 ]; then
    echo " ❌"
    echo "Dashboard failed to start. Last 30 log lines:"
    tail -30 /tmp/klinekit-web.log
    exit 1
  fi
done

echo
echo "----------------------------------------------------------"
echo "  ✨ klinekit is ready"
echo "  📊 dashboard:  http://localhost:$WEB_PORT"
echo "  🔌 API:        http://localhost:$API_PORT (Swagger: /swagger)"
echo "----------------------------------------------------------"
echo "  close this window or press Ctrl+C to stop everything"
echo

# Open browser (macOS).
if command -v open >/dev/null 2>&1; then
  open "http://localhost:$WEB_PORT"
fi

# Stay in foreground so the trap fires when user closes the window.
wait
