#!/usr/bin/env bash
# Loop-v19 — Production smoke test for Hey Pickler backend.
# Run after deploy to verify essential endpoints respond.
#
# Usage:
#   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
#   BASE_URL=https://api.example.com ./scripts/smoke-test.sh
#
# Exit code:
#   0  — all checks pass
#   1  — at least one check failed
#
# Required env:
#   BASE_URL     (default: http://localhost:8080)
#   ADMIN_TOKEN  (optional)  — if set, runs authed endpoints too

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"

pass=0
fail=0

run_check() {
  local label="$1"
  local expected_code="$2"
  local actual_code="$3"
  if [[ "$actual_code" == "$expected_code" ]]; then
    echo "  ✓ $label  (HTTP $actual_code)"
    pass=$((pass + 1))
  else
    echo "  ✗ $label  (expected $expected_code, got $actual_code)"
    fail=$((fail + 1))
  fi
}

check() {
  local label="$1"
  local path="$2"
  local expected_code="$3"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$BASE_URL$path" || echo "000")
  run_check "$label  $path" "$expected_code" "$code"
}

echo "== Hey Pickler smoke test =="
echo "  base url: $BASE_URL"
echo

echo "1. public health"
check "actuator health" "/actuator/health" "200"
check "actuator liveness" "/actuator/health/liveness" "200"

echo
echo "2. public app endpoints (anonymous)"
check "list events (app)" "/api/app/events?page=1&size=10" "200"
check "list banners" "/api/app/banners" "200"
check "list rankings" "/api/app/rankings?page=1&size=10" "200"

echo
echo "3. unauthenticated admin endpoints (must reject)"
check "list events (admin, no token)" "/api/admin/events?page=1&size=10" "401"
check "list users (admin, no token)" "/api/admin/users?page=1&size=10" "401"
check "dashboard (admin, no token)" "/api/admin/dashboard" "401"

if [[ -n "$ADMIN_TOKEN" ]]; then
  echo
  echo "4. authenticated admin endpoints (with token)"

  check_auth() {
    local label="$1"
    local path="$2"
    local expected_code="$3"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$BASE_URL$path" || echo "000")
    run_check "$label  $path" "$expected_code" "$code"
  }

  check_auth "list users (authed)" "/api/admin/users?page=1&size=10" "200"
  check_auth "list events (authed)" "/api/admin/events?page=1&size=10" "200"
  check_auth "dashboard (authed)" "/api/admin/dashboard" "200"
else
  echo
  echo "4. authenticated admin endpoints (skipped — no ADMIN_TOKEN set)"
  echo "   export ADMIN_TOKEN=<jwt> $0"
fi

echo
echo "== summary =="
echo "  passed: $pass"
echo "  failed: $fail"

if [[ "$fail" -gt 0 ]]; then
  echo
  echo "FAILED — investigate before declaring deploy healthy"
  exit 1
fi

echo
echo "OK — all essential endpoints respond as expected"
