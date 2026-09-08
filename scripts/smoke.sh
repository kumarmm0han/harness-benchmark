#!/usr/bin/env bash
# Primary acceptance journeys (AC-E2E-001 author->publish, AC-E2E-003 consumer read,
# FR-001 identity/permission, FR-050 filters, FR-043 version read) against the LIVE
# Compose stack (seeded with the BILL-REFUND-001 draft). The stack must already be up
# (e.g. `make demo`). Exits non-zero if ANY assertion fails (NFR-041, PRN-008).
#
# Requires: curl, jq.
set -uo pipefail

UI_BASE="${SOPDEMO_UI_BASE:-http://localhost:${SOPDEMO_UI_HOST_PORT:-13000}}/api/v1"
BE_BASE="${SOPDEMO_BACKEND_BASE:-http://localhost:${SOPDEMO_BACKEND_HOST_PORT:-18080}}"
AUTHOR="demo-author"
CONSUMER="demo-consumer"
SOP="BILL-REFUND-001"

P=0
F=0
ok()  { echo "  ok   $*"; P=$((P + 1)); }
bad() { echo "  FAIL $*"; F=$((F + 1)); }

# curl_json <identity> <method> <path> [json-body]
curl_json() {
  local identity="$1" method="$2" path="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -s -X "$method" -H "X-Demo-User: $identity" -H 'Content-Type: application/json' -d "$body" "${UI_BASE}${path}"
  else
    curl -s -X "$method" -H "X-Demo-User: $identity" "${UI_BASE}${path}"
  fi
}

# jq_expect <label> <jq-expr> <json>
jq_expect() {
  local label="$1" expr="$2" json="$3"
  if printf '%s' "$json" | jq -e "$expr" >/dev/null 2>&1; then ok "$label"; else bad "$label  (expr: $expr)"; fi
}

# code_expect <label> <expected> <actual-http-code>
code_expect() {
  local label="$1" exp="$2" act="$3"
  if [ "$act" = "$exp" ]; then ok "$label"; else bad "$label  (got HTTP $act, want $exp)"; fi
}

echo "==> SOP demo smoke — primary author -> consumer journey"
echo "    backend: $BE_BASE   UI: ${UI_BASE}   (seeded SOP: $SOP)"

# 0. backend is alive.
code_expect "backend is healthy (HTTP 200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$BE_BASE/healthz")"

# 1. author reads the seeded draft (AC-E2E-001 precondition, DR-003).
draft="$(curl_json "$AUTHOR" GET "/drafts/$SOP")"
jq_expect "author reads the seeded draft (revision >= 1, no failed marker)" \
  '.revision >= 1 and .publication_failed == false' "$draft"
rev="$(printf '%s' "$draft" | jq -r '.revision' 2>/dev/null || echo 0)"
src="$(printf '%s' "$draft" | jq -r '.source' 2>/dev/null)"

# 2. author validates the saved source -> valid, no issues (AC-E2E-001).
val="$(curl_json "$AUTHOR" POST "/validate" "$(jq -n --arg s "$src" '{source: $s}')")"
jq_expect "author POST /validate returns valid (AC-E2E-001)" \
  '.valid == true and (.issues | length == 0)' "$val"

# 3. author publishes by the saved revision -> canonical version 1 (AC-E2E-001, FR-042).
pub="$(curl_json "$AUTHOR" POST "/sops/$SOP/publish" "$(jq -n --argjson r "$rev" '{revision: $r}')")"
jq_expect "author POST /sops/{id}/publish -> canonical version 1 (AC-E2E-001)" \
  '.sop_id == "BILL-REFUND-001" and .version == 1 and .content.sop_id == "BILL-REFUND-001" and .content.intent == "refund_duplicate_charge"' "$pub"

# 4. catalog filter: domain AND risk lists it (FR-050).
list="$(curl_json "$CONSUMER" GET "/sops?domain=Billing&risk=medium")"
jq_expect "GET /sops?domain=Billing&risk=medium lists the SOP (v1, Billing, medium) (FR-050)" \
  'map(select(.sop_id == "BILL-REFUND-001" and .version == 1 and .domain == "Billing" and .risk == "medium")) | length == 1' "$list"

# 4b. AND-negative: a non-matching domain must NOT list it (FR-050).
list2="$(curl_json "$CONSUMER" GET "/sops?domain=Support&risk=medium")"
jq_expect "GET /sops?domain=Support does NOT list the Billing SOP (AND semantics)" \
  'map(select(.sop_id == "BILL-REFUND-001")) | length == 0' "$list2"

# 4c. invalid filter value -> 400 (FR-050 / IR-001). Must carry a valid identity so
# the controller is reached (otherwise the IdentityFilter returns 401 first).
code_expect "GET /sops?domain=Hacking -> 400 (invalid filter)" "400" \
  "$(curl -s -o /dev/null -w '%{http_code}' -H "X-Demo-User: $CONSUMER" "${UI_BASE}/sops?domain=Hacking")"

# 5. consumer reads the same canonical snapshot (AC-E2E-003, FR-052/053).
con="$(curl_json "$CONSUMER" GET "/sops/$SOP")"
jq_expect "consumer GET /sops/{id} -> same sop_id, version 1, policy (AC-E2E-003)" \
  '.sop_id == "BILL-REFUND-001" and .version == 1 and .content.sop_id == "BILL-REFUND-001" and .content.intent == "refund_duplicate_charge"' "$con"

# 6. consumer cannot mutate (AC-E2E-003, FR-001).
code_expect "consumer POST /validate -> 403" "403" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "X-Demo-User: $CONSUMER" -H 'Content-Type: application/json' -d '{"source":"x"}' "${UI_BASE}/validate")"

# 7. missing identity -> 401 (FR-001).
code_expect "request with no X-Demo-User -> 401" "401" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{"source":"x"}' "${UI_BASE}/validate")"

# 8. author reads an immutable historical version (FR-043).
v1="$(curl_json "$AUTHOR" GET "/sops/$SOP/versions/1")"
jq_expect "author GET /sops/{id}/versions/1 (immutable snapshot) (FR-043)" \
  '.version == 1 and .sop_id == "BILL-REFUND-001"' "$v1"

# 9. consumer is denied the author-only version endpoint (FR-001 / IR-001).
code_expect "consumer GET /sops/{id}/versions/1 -> 403" "403" \
  "$(curl -s -o /dev/null -w '%{http_code}' -H "X-Demo-User: $CONSUMER" "${UI_BASE}/sops/$SOP/versions/1")"

echo "============================================================"
echo "  passed: $P   failed: $F"
if [ "$F" -gt 0 ]; then
  echo "SMOKE: FAILED"
  exit 1
fi
echo "SMOKE: PASSED"
exit 0
