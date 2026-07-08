#!/usr/bin/env bash
# Loop-v19 — Performance baseline for Hey Pickler backend.
# Uses `hey` (https://github.com/rakyll/hey) if available, otherwise `ab`.
# Runs a quick smoke load against key endpoints and prints p50/p95/p99.
#
# Usage:
#   BASE_URL=http://localhost:8080 ./scripts/perf-baseline.sh
#   CONCURRENCY=50 REQUESTS=2000 ./scripts/perf-baseline.sh
#
# Required env:
#   BASE_URL    (default http://localhost:8080)
#   CONCURRENCY (default 20)
#   REQUESTS    (default 1000)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-20}"
REQUESTS="${REQUESTS:-1000}"

if ! command -v hey >/dev/null 2>&1; then
  echo "::warning::'hey' not installed. Install with: go install github.com/rakyll/hey@latest"
  echo "Falling back to 'ab' (ApacheBench)..."
fi

run() {
  local name="$1"
  local path="$2"
  local method="${3:-GET}"
  echo
  echo "== $name ($method $path) =="

  if command -v hey >/dev/null 2>&1; then
    hey -n "$REQUESTS" -c "$CONCURRENCY" -m "$method" "$BASE_URL$path"
  else
    ab -n "$REQUESTS" -c "$CONCURRENCY" -X "$method" "$BASE_URL$path" 2>&1 | grep -E "Requests per|Time per|Percentage|Failed"
  fi
}

echo "== perf baseline =="
echo "  base url: $BASE_URL"
echo "  concurrency: $CONCURRENCY"
echo "  total requests per endpoint: $REQUESTS"
echo "  tool: $(command -v hey >/dev/null 2>&1 && echo hey || echo ab)"

run "list events (public app)" "/api/app/events?page=1&size=10"

run "list events (admin authed)" "/api/admin/events?page=1&size=10"
# Note: admin endpoint without auth → 401. Above call may skew p99 if Spring
# Boot's exception handler dominates. To smoke-test admin perf, set
# ADMIN_TOKEN=... and add a 4th run here.

echo
echo "== done =="
echo "Targets (from Loop-v18 RUNBOOK §9):"
echo "  API p99 < 2s  (200ms for /api/app/* ; 500ms for /api/admin/*)"
echo "  5xx error rate < 1%"
