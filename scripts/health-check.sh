#!/usr/bin/env bash
# Loop-v19 — Minimal health monitor for Hey Pickler backend.
# Polls /actuator/health and fires a webhook on SUSTAINED failure (and on recovery).
# Designed for cron (every minute). The fail-count threshold avoids alerting on a
# single network blip. Exits 0 on every path so cron doesn't mail on every poll —
# the webhook is the signal.
#
# Usage (cron — every minute):
#   * * * * *  HEALTH_URL=http://localhost:8080/actuator/health \
#             ALERT_WEBHOOK=https://oapi.dingtalk.com/robot/send?access_token=... \
#             /opt/hey-pickler/scripts/health-check.sh >> /var/log/hey_pickler_health.log 2>&1
#
# Required:
#   HEALTH_URL     (e.g. http://localhost:8080/actuator/health)
# Optional:
#   ALERT_WEBHOOK  — DingTalk-compatible JSON webhook. If unset, failures are only logged.
#   FAIL_THRESHOLD (default: 3)  — consecutive failures before alerting
#   TIMEOUT_SEC    (default: 5)
#   STATE_FILE     (default: /tmp/hey_pickler_health.state) — persists consecutive fail count
#   HOST_LABEL     (default: $(hostname)) — included in alert payload

set -euo pipefail

: "${HEALTH_URL:?HEALTH_URL is required (e.g. http://localhost:8080/actuator/health)}"
ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"
FAIL_THRESHOLD="${FAIL_THRESHOLD:-3}"
TIMEOUT_SEC="${TIMEOUT_SEC:-5}"
STATE_FILE="${STATE_FILE:-/tmp/hey_pickler_health.state}"
HOST_LABEL="${HOST_LABEL:-$(hostname)}"

# Fire a DingTalk-compatible text message. (For Slack, switch the payload to {"text": ...}.)
# Content is JSON-escaped via python3 to avoid breakage on quotes/special chars in HOST_LABEL.
send_alert() {
  local status="$1" http_code="$2" fails="$3"
  [ -n "$ALERT_WEBHOOK" ] || return 0
  local ts; ts="$(date -u +%FT%TZ)"
  local text
  text="$(printf '🔴 [Hey Pickler] %s\nhost: %s\nurl: %s\nhttp: %s (consecutive fails: %s)\ntime: %s' \
    "$status" "$HOST_LABEL" "$HEALTH_URL" "$http_code" "$fails" "$ts")"
  local payload
  payload="$(printf '{"msgtype":"text","text":{"content":%s}}' \
    "$(printf '%s' "$text" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')" )"
  if ! curl -fsS -X POST "$ALERT_WEBHOOK" -H "Content-Type: application/json" -d "$payload" >/dev/null 2>&1; then
    echo "$(date -u +%FT%TZ) ::warning:: alert webhook delivery failed" >&2
  fi
}

cur=0
[ -f "$STATE_FILE" ] && cur="$(cat "$STATE_FILE" 2>/dev/null | tr -dc '0-9')"
[ -z "$cur" ] && cur=0

# 000 = total connect failure / timeout; curl -f is NOT used so non-200 returns its real code.
http_code="$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT_SEC" "$HEALTH_URL" || echo "000")"

if [ "$http_code" = "200" ]; then
  if [ "$cur" -ge "$FAIL_THRESHOLD" ]; then
    send_alert "RECOVERED" "200" "$cur"
  fi
  echo 0 > "$STATE_FILE"
  echo "$(date -u +%FT%TZ) OK (200) fails=0"
  exit 0
fi

cur=$((cur + 1))
echo "$cur" > "$STATE_FILE"
echo "$(date -u +%FT%TZ) FAIL ($http_code) fails=$cur/$FAIL_THRESHOLD" >&2

if [ "$cur" -ge "$FAIL_THRESHOLD" ]; then
  send_alert "DOWN" "$http_code" "$cur"
fi

exit 0
