#!/usr/bin/env bash
# TASK-014: HTTP smoke of the primary author -> consumer journey against the
# running Compose stack. Run after `make demo`.
#
#   BASE   API base (default http://localhost:3010/api/v1, i.e. through the
#          UI origin; set to http://localhost:8080/api/v1 to hit the backend
#          directly). Exits nonzero on the first failure.
set -euo pipefail

BASE="${BASE:-http://localhost:3010/api/v1}"
AUTHOR_HDR='X-Demo-User: demo-author'
CONSUMER_HDR='X-Demo-User: demo-consumer'
SOP_ID='SMOKE-001'

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
step() { echo "smoke: $*"; }

# jget <json> <python-expression> [extra-arg...]: parses <json> into `d` and
# prints the result of the expression. Bash variables are passed as extra
# args (sys.argv[2], sys.argv[3], ...) and never substituted into the code.
jget() {
  local json="$1" expr="$2"; shift 2
  python3 - "$json" "$@" <<PYEOF
import json, sys
d = json.loads(sys.argv[1])
expr = """$expr"""
print(eval(expr, {"d": d, "sys": sys}))
PYEOF
}

# --- 0. reachability ---------------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:3010/")
[ "$code" = "200" ] || fail "UI origin not reachable (HTTP $code)"
step "UI origin reachable"

# --- 1. author: validate -> save -> publish ----------------------------------
src=$(python3 - <<'EOF'
print('''---
sop_id: SMOKE-001
title: Smoke Check Refund
owner_team: Platform QA
domain: Billing
intent: refund_duplicate_charge
risk_level: medium
max_autonomy: assist
---

## Intent (When to use)
- A smoke run confirms the full author to consumer journey.

## Do Not Use When
- The environment is not the local demo stack.

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
      value: 100
  action_ids: [A1]
- id: R2
  conditions:
    - input: refund_amount
      op: gt
      value: 100
  action_ids: [A2]
```

## Actions
```yaml
- id: A1
  kind: refund
  description: A representative may process a confirmed smoke refund within the limit.
  max_amount: 100
- id: A2
  kind: escalate
  description: Refer an over-limit request for review.
```

## Boundaries
```yaml
escalation:
  - action_id: A1
    input: refund_amount
    op: gt
    amount: 100
    target_action_id: A2
```

## Customer Messages
```yaml
primary: A representative can review this confirmed refund for the smoke journey.
escalation: This request needs additional review because it exceeds the limit.
```''')
EOF
)

req_body() { python3 -c 'import json,sys; print(json.dumps({"source": sys.argv[1]}))' "$1"; }

val=$(curl -s -H "$AUTHOR_HDR" -H 'Content-Type: application/json' \
  -d "$(req_body "$src")" "$BASE/validate")
valid=$(jget "$val" 'd["valid"]')
[ "$valid" = "True" ] || fail "validate expected valid=true, got: $val"
n_issues=$(jget "$val" 'len(d["issues"])')
step "validate: valid ($n_issues issues)"

save=$(curl -s -X PUT -H "$AUTHOR_HDR" -H 'Content-Type: application/json' \
  -d "$(req_body "$src")" "$BASE/drafts/$SOP_ID")
revision=$(jget "$save" 'd["revision"]')
step "draft saved (revision=$revision)"

pub=$(curl -s -H "$AUTHOR_HDR" -H 'Content-Type: application/json' \
  -d "{ \"revision\": $revision }" "$BASE/sops/$SOP_ID/publish")
version=$(jget "$pub" 'd["version"]')
envelope_ok=$(jget "$pub" 'd["sop_id"] and d["content"]["sop_id"]' "$SOP_ID")
[ "$envelope_ok" == "$SOP_ID" ] || fail "publish envelope sop_id must equal content sop_id: $pub"
step "published sop=$SOP_ID version=$version (envelope sop_id == content sop_id)"

# --- 2. consumer: list + detail of the same snapshot --------------------------
list=$(curl -s -H "$CONSUMER_HDR" "$BASE/sops?domain=Billing&risk=medium")
in_list=$(jget "$list" 'any(s["sop_id"] == sys.argv[2] and s["version"] == int(sys.argv[3])
      for s in d["sops"])' "$SOP_ID" "$version")
[ "$in_list" = "True" ] || fail "consumer list (domain=Billing&risk=medium) missing SMOKE-001 v$version: $list"
step "consumer list shows $SOP_ID v$version (domain/risk filter matched)"

detail=$(curl -s -H "$CONSUMER_HDR" "$BASE/sops/$SOP_ID")
dver=$(jget "$detail" 'd["version"]')
[ "$dver" = "$version" ] || fail "consumer detail version $dver != published $version"
title=$(jget "$detail" 'd["content"]["title"]')
step "consumer detail: version=$dver title=$title"

# --- 3. history: author yes, consumer no (403) --------------------------------
hist=$(curl -s -H "$AUTHOR_HDR" "$BASE/sops/$SOP_ID/versions/$version")
[ "$(jget "$hist" 'd["version"]')" = "$version" ] || fail "author version history read failed: $hist"
step "author read historical version $version"

code=$(curl -s -o /dev/null -w '%{http_code}' -H "$CONSUMER_HDR" "$BASE/sops/$SOP_ID/versions/$version")
[ "$code" = "403" ] || fail "consumer version-history expected 403, got $code"
step "consumer version-history denied (403)"

# --- 4. identity enforcement ---------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/sops")
[ "$code" = "401" ] || fail "missing identity expected 401, got $code"
step "missing identity denied (401)"

code=$(curl -s -o /dev/null -w '%{http_code}' -H "$CONSUMER_HDR" -H 'Content-Type: application/json' \
  -d '{"source": "x"}' -X PUT "$BASE/drafts/$SOP_ID")
[ "$code" = "403" ] || fail "consumer draft save expected 403, got $code"
step "consumer draft save denied (403)"

echo "SMOKE PASS: author validate -> save (rev $revision) -> publish v$version -> consumer list/detail; history author-only; identity enforced."
