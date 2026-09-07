#!/usr/bin/env bash
# wait-for-health.sh BASE_URL TIMEOUT — poll frontend /healthz and the backend API
# (via the nginx proxy) until both are 200. Polls, never fixed-sleeps (NFR-001).
set -euo pipefail
BASE="${1:-http://localhost:8075}"
TIMEOUT="${2:-90}"
start=$(date +%s)
while :; do
  if [ "$(curl -fs -o /dev/null -w '%{http_code}' "$BASE/healthz" 2>/dev/null || echo 000)" = "200" ] \
     && [ "$(curl -fs -o /dev/null -w '%{http_code}' "$BASE/api/v1/sops" -H 'X-Demo-User: demo-author' 2>/dev/null || echo 000)" = "200" ]; then
    echo "healthy: $BASE"
    exit 0
  fi
  now=$(date +%s)
  if [ $((now - start)) -ge "$TIMEOUT" ]; then
    echo "timed out waiting for $BASE to become healthy" >&2
    exit 1
  fi
  sleep 1
done
