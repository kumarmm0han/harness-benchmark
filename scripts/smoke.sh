#!/usr/bin/env bash
# smoke.sh — primary author→consumer journey against the running Compose stack (NFR-041).
# Usage: scripts/smoke.sh [BASE_URL]     (default http://localhost:8075)
# Exits 0 on success, nonzero (with STEP failures) otherwise. Uses curl + jq + python3.
set -euo pipefail

BASE="${1:-http://localhost:8075}"
AUTHOR="demo-author"
CONSUMER="demo-consumer"
SOP="BILL-REFUND-001"

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
step() { echo "[$*]"; }

command -v curl >/dev/null || fail "curl not found"
command -v jq   >/dev/null || fail "jq not found"

# --- wait for service readiness (no fixed sleeps) ---
step "waiting for services"
for i in $(seq 1 60); do
  if [ "$(curl -fs -o /dev/null -w '%{http_code}' "$BASE/healthz" 2>/dev/null || echo 000)" = "200" ]; then break; fi
  sleep 1
done
[ "$(curl -fs -o /dev/null -w '%{http_code}' "$BASE/healthz" 2>/dev/null)" = "200" ] || fail "frontend /healthz not 200"
for i in $(seq 1 60); do
  [ "$(curl -fs -o /dev/null -w '%{http_code}' "$BASE"/api/v1/sops -H "X-Demo-User: $AUTHOR" 2>/dev/null || echo 000)" = "200" ] && break
  sleep 1
done
curl -fs "$BASE/api/v1/sops" -H "X-Demo-User: $AUTHOR" >/dev/null || fail "backend API not ready via proxy"
step "services healthy"

# The valid template (spec §3). Quoted heredoc preserves the ```yaml fences.
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/template.md" <<'EOF'
---
sop_id: BILL-REFUND-001
title: Refund for Duplicate Charge
owner_team: Billing Operations
domain: Billing
intent: refund_duplicate_charge
risk_level: medium
max_autonomy: assist
---

## Intent (When to use)
- Customer reports a duplicate charge.
- A support representative confirms the duplicate.

## Do Not Use When
- Fraud is suspected.

## Inputs Required
```yaml
- name: refund_amount
  type: number
- name: duplicate_confirmed
  type: boolean
```

## Eligibility Rules
```yaml
- id: R1
  conditions:
    - input: duplicate_confirmed
      op: eq
      value: true
    - input: refund_amount
      op: lte
      value: 200
  action_ids: [A1]
- id: R2
  conditions:
    - input: refund_amount
      op: gt
      value: 200
  action_ids: [A2]
```

## Actions
```yaml
- id: A1
  kind: refund
  description: A representative may process a confirmed duplicate refund within the limit.
  max_amount: 200
- id: A2
  kind: escalate
  description: Refer an over-limit request to Billing Support for review.
```

## Boundaries
```yaml
escalation:
  - action_id: A1
    input: refund_amount
    op: gt
    amount: 200
    target_action_id: A2
```

## Customer Messages
```yaml
primary: A representative can review the confirmed duplicate charge for a refund.
escalation: This request needs additional review because it exceeds the refund limit.
```
EOF

# --- 1. save the draft as author -> capture revision ---
step "1/6  PUT /drafts/$SOP (author)"
body1="$(jq -Rs '{source: .}' < "$TMP/template.md")"
resp1="$(curl -fs -X PUT "$BASE/api/v1/drafts/$SOP" -H "X-Demo-User: $AUTHOR" \
  -H 'Content-Type: application/json' -d "$body1")" || fail "save draft"
REV="$(echo "$resp1" | jq -r '.revision')"
[[ "$REV" =~ ^[0-9]+$ ]] || fail "no revision in save response: $resp1"
echo "     saved revision=$REV"

# --- 2. validate -> 200 valid:true ---
step "2/6  POST /validate (author)"
resp2="$(curl -fs -X POST "$BASE/api/v1/validate" -H "X-Demo-User: $AUTHOR" \
  -H 'Content-Type: application/json' -d "$body1")" || fail "validate"
VALID="$(echo "$resp2" | jq -r '.valid')"
[ "$VALID" = "true" ] || fail "validation not valid: $resp2"
echo "     valid=true"

# --- 3. publish by its revision -> version 1 (or the next one) ---
step "3/6  POST /sops/$SOP/publish (author, revision=$REV)"
resp3="$(curl -fs -X POST "$BASE/api/v1/sops/$SOP/publish" -H "X-Demo-User: $AUTHOR" \
  -H 'Content-Type: application/json' -d "$(jq -n --argjson r "$REV" '{revision:$r}')")" || fail "publish"
VER="$(echo "$resp3" | jq -r '.version')"
[[ "$VER" =~ ^[0-9]+$ ]] || fail "no version in publish response: $resp3"
[ "$(echo "$resp3" | jq -r '.sop_id')" = "$SOP" ] || fail "published sop_id mismatch"
echo "     published version=$VER"

# --- 4. list + filter includes it ---
step "4/6  GET /sops?domain=Billing&risk=medium"
resp4="$(curl -fs "$BASE/api/v1/sops?domain=Billing&risk=medium" -H "X-Demo-User: $CONSUMER")" || fail "list"
FOUND="$(echo "$resp4" | jq -r --arg s "$SOP" '.[] | select(.sop_id==$s) | .version' 2>/dev/null || true)"
[ "$FOUND" = "$VER" ] || fail "sop not in filtered list (expected version $VER): $resp4"
echo "     listed with version=$FOUND"

# --- 5. consumer detail -> 200, same sop_id+version as the published snapshot ---
step "5/6  GET /sops/$SOP (consumer)"
resp5="$(curl -fs "$BASE/api/v1/sops/$SOP" -H "X-Demo-User: $CONSUMER")" || fail "consumer detail"
[ "$(echo "$resp5" | jq -r '.sop_id')" = "$SOP" ] || fail "detail sop_id mismatch"
[ "$(echo "$resp5" | jq -r '.version')" = "$VER" ] || fail "detail version mismatch (expected $VER)"
[ "$(echo "$resp5" | jq -r '.content.domain')" = "Billing" ] || fail "content.domain missing"
echo "     detail sop_id=$SOP version=$VER"

# --- 6. consumer mutation is forbidden (403) ---
step "6/6  consumer mutation -> 403"
CODE_PUB="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/sops/$SOP/publish" \
  -H "X-Demo-User: $CONSUMER" -H 'Content-Type: application/json' -d '{"revision":1}')"
[ "$CODE_PUB" = "403" ] || fail "consumer publish should be 403, got $CODE_PUB"
CODE_DRAFTS="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/drafts" -H "X-Demo-User: $CONSUMER")"
[ "$CODE_DRAFTS" = "403" ] || fail "consumer draft list should be 403, got $CODE_DRAFTS"
echo "     consumer publish=403, draft list=403"

step "SMOKE PASS — primary journey (save→validate→publish→list→detail→permission) OK at $BASE"
