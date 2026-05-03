#!/usr/bin/env bash
# One-shot dev launcher: boots the Spring Boot API (H2 in-memory) and the
# Next.js dashboard. Both processes are killed when this script exits.
#
# Usage:
#   ./scripts/dev.sh
#
# Then open http://localhost:3000 in your browser.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API_PORT="${API_PORT:-8080}"
WEB_PORT="${WEB_PORT:-3000}"

cleanup() {
  echo
  echo "[dev.sh] shutting down..."
  jobs -p | xargs -r kill 2>/dev/null || true
  pkill -f KlinekitApplication 2>/dev/null || true
  pkill -f "next dev" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "[dev.sh] starting API (Spring Boot + H2) on :$API_PORT"
SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$API_PORT" \
  ./gradlew :api:bootRun --quiet &

echo "[dev.sh] waiting for API..."
for i in $(seq 1 40); do
  if curl -fsS "http://localhost:$API_PORT/v3/api-docs" >/dev/null 2>&1; then
    echo "[dev.sh] API ready at http://localhost:$API_PORT"
    break
  fi
  sleep 2
done

echo "[dev.sh] starting web dashboard on :$WEB_PORT"
(cd web && PORT="$WEB_PORT" NEXT_PUBLIC_KLINEKIT_API="http://localhost:$API_PORT/api/v1" npm run dev) &

echo
echo "  → API:       http://localhost:$API_PORT (Swagger UI: /swagger)"
echo "  → Dashboard: http://localhost:$WEB_PORT"
echo
echo "Press Ctrl+C to stop both."

wait
