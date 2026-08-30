# SOP System Demo Context (for Codex)

This document provides the **canonical SOP model**, **taxonomy**, **authoring workflow (Option A: Template + Validation)**, and **validation rules** to help build a **demoable app** that supports:
- Business authoring in **Bloomfire-style structured Markdown** (Markdown + YAML front matter + YAML blocks)
- Automatic **parsing + validation**
- Compilation into a **canonical JSON SOP object**
- Serving SOPs to **Humans** (rendered view) and **AI Agents** (structured API)

---

## 1) Product Goal

Build a demo app that demonstrates a **Single Source of Truth SOP system** where:

1. **Business users author SOPs** using a structured Markdown template (copy/paste in a UI editor).
2. A backend **parses** the Markdown (front matter + required sections).
3. The system **validates**:
   - Schema checks (required fields, correct types, enums)
   - Semantic checks (policy safety rules by risk level)
4. On publish, the system **compiles** the SOP to a **canonical JSON model**.
5. The app provides two consumption views:
   - **Human View**: readable SOP page with UI guidance + screenshots
   - **AI View / API**: canonical JSON including decision rules, actions, boundaries

---

## 2) Canonical SOP Model (JSON) — Source of Truth

Use this as the internal model stored and served by the app (e.g., in Postgres).

### 2.1 JSON Schema Shape (high-level)

- `sop_id`, `title`, `status`, `version`, `effective`
- `ownership` (owner team/contact, approver roles)
- `scope` (region, channel, segment, product, systems)
- `classification` (domain, capability, journey, intents, tags)
- `risk` (risk_level, regulatory, max_autonomy)
- `policy` (use_when, do_not_use_when, definitions)
- `decision_model` (inputs_required, rules)
- `actions` (tool calls or human assist)
- `boundaries` (allowed/disallowed actions, approvals, escalation triggers)
- `customer_messages`
- `human_guidance` (optional UI steps + screenshot references)
- `references` (related SOPs, source link)
- `changelog`

### 2.2 Example Canonical SOP JSON (sample)

```json
{
  "sop_id": "BILL-REFUND-001",
  "title": "Refund for Duplicate Charge",
  "status": "active",
  "version": "3.2.0",
  "effective": { "start": "2026-02-01T00:00:00Z", "end": null },
  "ownership": {
    "owner_team": "Billing Operations",
    "owner_contact": "billing-ops@company.com",
    "approver_roles": ["QA Lead", "Compliance"]
  },
  "scope": {
    "region": ["US"],
    "language": ["en"],
    "channel": ["voice", "chat", "web"],
    "customer_segment": ["consumer"],
    "product": ["postpaid"],
    "systems": ["CRM_X", "Billing_Y"]
  },
  "classification": {
    "domain": "Billing",
    "capability": "Refunds",
    "journey": "Charge Dispute",
    "intents": ["refund_duplicate_charge", "billing_error_refund"],
    "tags": ["refund", "duplicate", "billing_dispute"]
  },
  "risk": {
    "risk_level": "medium",
    "regulatory": false,
    "financial_impact": true,
    "max_autonomy": "guardrailed"
  },
  "policy": {
    "purpose": "Process refunds for confirmed duplicate charges within allowed thresholds.",
    "use_when": [
      "Customer reports being charged twice for the same transaction",
      "Duplicate charge is visible in billing system"
    ],
    "do_not_use_when": [
      "Fraud is suspected",
      "Charge is older than the allowed refund window"
    ],
    "definitions": [
      { "term": "duplicate_charge", "definition": "Two identical charges for the same product/service on same billing cycle." }
    ]
  },
  "decision_model": {
    "inputs_required": [
      { "name": "charge_age_days", "type": "number" },
      { "name": "refund_amount", "type": "number" },
      { "name": "fraud_flag", "type": "boolean" },
      { "name": "duplicate_confirmed", "type": "boolean" }
    ],
    "rules": [
      {
        "id": "R1",
        "if": "fraud_flag == true",
        "then": { "route": "use_sop", "sop_id": "FRAUD-INV-002" },
        "priority": 1
      },
      {
        "id": "R2",
        "if": "duplicate_confirmed == true && charge_age_days <= 30 && refund_amount <= 200",
        "then": { "outcome": "approve_refund", "actions": ["A1"] },
        "priority": 2
      },
      {
        "id": "R3",
        "if": "duplicate_confirmed == true && (charge_age_days > 30 || refund_amount > 200)",
        "then": { "outcome": "escalate", "actions": ["A2"] },
        "priority": 3
      }
    ]
  },
  "actions": [
    {
      "id": "A1",
      "name": "process_refund",
      "type": "tool_call",
      "tool": "billing_api",
      "parameters": { "amount": "{{refund_amount}}", "reason_code": "DUP_CHARGE" },
      "constraints": { "max_amount": 200 },
      "audit": { "log_event": true, "fields": ["amount", "reason_code", "case_id"] }
    },
    {
      "id": "A2",
      "name": "create_escalation_ticket",
      "type": "tool_call",
      "tool": "ticketing_api",
      "parameters": { "queue": "Billing_Tier2", "category": "RefundReview" }
    }
  ],
  "boundaries": {
    "allowed_actions": ["process_refund", "create_escalation_ticket"],
    "disallowed_actions": [
      "refund_without_duplicate_confirmation",
      "override_refund_limit_without_approval"
    ],
    "approvals": [
      { "condition": "refund_amount > 200", "required_role": "Billing Supervisor" }
    ],
    "escalation_triggers": [
      "fraud_flag == true",
      "refund_amount > 200",
      "customer_threatens_legal_action == true"
    ]
  },
  "customer_messages": {
    "refund_approved": "I’ve confirmed the duplicate charge and processed your refund. You should see it in 3–5 business days.",
    "needs_review": "I can see the issue and I’m submitting this for additional review."
  },
  "human_guidance": {
    "ui_procedure": [
      {
        "system": "CRM_X",
        "version": "v12",
        "steps": [
          "Open Billing tab for the customer",
          "Locate the two matching charges",
          "Select 'Refund Adjustment' and enter amount",
          "Submit and record case ID"
        ],
        "attachments": [
          { "type": "image", "name": "refund_step1.png", "caption": "Billing tab location" }
        ]
      }
    ]
  },
  "changelog": [
    { "version": "3.2.0", "date": "2026-02-01", "change": "Refund window set to 30 days; cap remains $200." }
  ]
}
```

---

## 3) Authoring Format in the Demo (Bloomfire-style Markdown)

Business users author a single Markdown document with:

1. **YAML Front Matter** for metadata and scope  
2. **Required headings** for sections  
3. YAML code blocks for **rules/actions/boundaries/messages** (machine-critical content)

### 3.1 Minimum Viable SOP Template (for the demo)

```markdown
---
sop_id: <DOMAIN>-<CAPABILITY>-<###>
title: "<Short title>"
status: draft            # draft | active | deprecated
version: 0.1.0
effective_start: YYYY-MM-DD
owner_team: "<Team name>"
owner_contact: "<group email or slack channel>"

domain: <Domain>
capability: <Capability>
journey: "<Journey name>"
intents: [<intent_1>, <intent_2>]

risk_level: low          # low | medium | high | critical
regulatory: false        # true | false
max_autonomy: assist     # assist | guardrailed | autonomous

region: [US]
channel: [voice, chat]
customer_segment: [consumer]
product: [postpaid]
systems: [CRM_X, Billing_Y]
---

## Intent (When to use)
- <1–3 bullets>

## Do Not Use When
- <1–3 bullets>

## Inputs Required
- refund_amount (number)

## Eligibility Rules
```yaml
- id: R1
  if: <condition>
  then: { outcome: <outcome>, actions: [A1] }
```

## Actions
```yaml
- id: A1
  name: <action_name>
  type: tool_call           # tool_call | human_assist
  tool: <tool_name>         # required if tool_call
  parameters: {}
  constraints: {}
```

## Boundaries (Required)
```yaml
allowed_actions: [<action_name>]
disallowed_actions: [<disallowed_action_1>]
escalation_triggers: [<trigger_1>]
approvals: []
```

## Customer Messages (Required)
```yaml
primary: "<approved message>"
escalation: "<escalation message>"
```

## Human UI Guidance (Optional)
- System/version:
  1. Step...
  2. Step...
- Attach screenshots below
```

**Rule:** Machine-critical logic must live in YAML blocks, not in prose.

---

## 4) Option A Workflow (Template + Validation)

### Roles
- **Business SME (Author)**: edits SOP template content
- **KM (Publisher/Curator)**: ensures template compliance; publishes
- **QA/Compliance (Approver)**: required by risk tier
- **Automation team**: consumes canonical JSON via API

### Lifecycle
1. Draft SOP in structured Markdown
2. Submit for review
3. Automated validation runs (schema + semantics)
4. Human approvals (risk-tier based)
5. Publish (compile to canonical JSON)
6. Notify + refresh AI store (demo can simulate this)

---

## 5) Validation (Runtime + Publish-time)

### 5.1 Schema validation (strict)
Validate front matter:
- Required fields: `sop_id, title, status, version, effective_start, owner_team, domain, capability, intents, risk_level, max_autonomy`
- Enums:
  - `status ∈ {draft, active, deprecated}`
  - `risk_level ∈ {low, medium, high, critical}`
  - `max_autonomy ∈ {assist, guardrailed, autonomous}`
- Formats: dates, semver (optional)

Validate required sections exist:
- Intent, Do Not Use When, Inputs Required, Eligibility Rules, Actions, Boundaries, Customer Messages

Validate references:
- Rule action IDs exist
- Required inputs referenced in rules exist in Inputs Required

### 5.2 Semantic validation (business safety)
Examples:
- If intent/tag implies money (`refund`, `credit`) then:
  - actions must include `constraints.max_amount` (or equivalent)
  - boundaries must include escalation for `amount > max_amount`
- If `risk_level == high`:
  - approvals must include at least one rule
  - max_autonomy cannot be `autonomous` unless explicitly allowed
- If `regulatory == true` or `risk_level == critical`:
  - max_autonomy must be `assist` or strict `guardrailed`
  - audit fields required for tool actions

### 5.3 Last-known-good fallback (recommended)
If an active SOP fails validation at runtime:
- Serve the last validated version
- Flag an alert in the UI

---

## 6) Taxonomy Draft (Controlled Vocabulary)

### 6.1 Business classification: Domain → Capability → Journey
Domains (starter):
- Account & Identity
- Billing & Payments
- Plans & Subscriptions
- Orders & Fulfillment
- Technical Support
- Service Changes
- Complaints & Retention
- Fraud & Security
- Compliance & Privacy
- General Information

Example capabilities:
- Billing & Payments: Charges & Invoices, Refunds & Credits, Payment Methods, Collections
- Account & Identity: Authentication, Profile Changes, Identity Verification
- Technical Support: Connectivity, Troubleshooting, Outages

Journeys (examples):
- Charge Dispute
- Duplicate Charge Refund
- Cancel Subscription
- Reset Password
- Report Fraud
- Outage Inquiry

### 6.2 Intent taxonomy (AI-friendly)
Naming convention: `verb_object[_context]`

Examples:
- `reset_password`
- `unlock_account`
- `update_payment_method`
- `refund_duplicate_charge`
- `apply_goodwill_credit`
- `cancel_subscription`
- `check_outage_status`
- `report_fraud`

### 6.3 Operational facets (filters)
- region, channel, customer_segment, product, systems, language
- risk_level, regulatory, max_autonomy
- change_frequency (optional): high/medium/low

---

## 7) Demo App Features (Suggested)

### UI Pages
1. **SOP List** with filters: domain, intent, risk, status, region, channel
2. **SOP Detail (Human View)**:
   - Rendered sections + UI guidance + attachments
3. **SOP Detail (AI View)**:
   - Canonical JSON preview
4. **Editor Page**:
   - Markdown editor with template insert button
   - Validate button (shows human-friendly errors)
5. **Publish Flow**:
   - Risk-tier approvals simulation
   - Publish converts Markdown → JSON and marks active

### Backend Endpoints
- `POST /parse` → returns parsed object + validation results
- `POST /validate` → returns schema + semantic violations
- `POST /publish` → stores canonical JSON + returns version
- `GET /sops` → filterable list
- `GET /sops/:id` → canonical JSON
- `GET /sops/:id/rendered` → human view (server-render or client render)

---

## 8) Decision Authority (Guardrails) Model (for the demo)

For each SOP, enforce:
- `max_autonomy`:
  - assist: no tool execution
  - guardrailed: tool execution only within constraints
  - autonomous: tool execution allowed with minimal checks
- `boundaries.approvals`:
  - if approval required, demo can simulate human approval step
- `boundaries.escalation_triggers`:
  - if trigger detected, escalate to human queue

---

## 9) Notes for Implementation

- Use a Markdown parser that supports front matter extraction.
- Parse required headings by searching for `## <Section Name>`.
- Parse YAML blocks under specific headings.
- Normalize to canonical JSON and store.
- Validation errors should be business-readable.

---

## 10) Acceptance Criteria (Demo)

A successful demo should show:
- Business user can create an SOP via template
- Validation catches missing limits/fields and explains them clearly
- Publish produces canonical JSON
- Human view and AI JSON view are derived from the same SOP
- Filters and taxonomy work (domain/capability/intent/scope)
- Changes increment version and can be activated with effective dates
