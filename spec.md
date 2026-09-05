# SOP Demo — Supported Content Contract

This document defines the template and canonical content for the trimmed demo in `REQUIREMENTS.md`. It replaces the earlier broad model, taxonomy, and approval/effective-date workflow. Only the fields below are supported; additional fields and sections are errors.

## 1. Authoring format

A source document begins with YAML front matter between `---` delimiter lines, followed by these exact level-two headings, each appearing once:

- `Intent (When to use)` — one or more Markdown bullet lines, compiled as strings
- `Do Not Use When` — one or more Markdown bullet lines, compiled as strings
- `Inputs Required` — one YAML block containing a list
- `Eligibility Rules` — one YAML block containing a list
- `Actions` — one YAML block containing a list
- `Boundaries` — one YAML block containing an object
- `Customer Messages` — one YAML block containing an object

Blank lines are allowed. Prose sections support plain bullet text only; inline markup/HTML is retained as text, not interpreted. Machine sections contain exactly one fenced `yaml` block and no other nonblank content. There are no attachments, optional guidance sections, nested Markdown headings, or executable expressions.

Front matter requires `sop_id`, `title`, `owner_team`, `domain`, `intent`, `risk_level`, and `max_autonomy`, all strings. `sop_id` matches `[A-Z][A-Z0-9-]{0,63}`. Title and owner team are nonempty. Domains are `Billing` or `Support`; intents are `refund_duplicate_charge` or `answer_question`; risk levels are `low` or `medium`; max autonomy is `assist`. The refund intent requires domain `Billing`. Status, version, and timestamps are server metadata and cannot be authored.

Reject unknown keys at every object level, duplicate YAML keys, aliases, custom tags, non-finite numbers, and incorrectly typed values. Source is limited to 65,536 UTF-8 bytes. YAML collection nesting is limited to 20 levels, counting a root mapping/list as level 1. These restrictions apply to front matter and section YAML alike.

## 2. Machine-critical structures

| Structure | Required fields and constraints |
|---|---|
| Input | `name`: `[a-z][a-z0-9_]{0,63}`; `type`: `number` or `boolean`; unique names |
| Rule | `id`: nonempty identifier; `conditions`: nonempty list of conditions; `action_ids`: nonempty list of existing action IDs; unique rule IDs |
| Condition | `input`: declared input name; `op`: `eq`, `gt`, or `lte`; `value`: number for numeric inputs, boolean for boolean inputs; booleans support `eq` only |
| Action | `id`: nonempty identifier; `kind`: `refund`, `escalate`, or `human_assist`; `description`: nonempty text; `max_amount`: positive finite number required for refund, disallowed for other kinds |
| Boundaries | `escalation`: list of escalation objects (empty allowed for nonfinancial SOPs) |
| Escalation | `action_id`: existing refund action; `input`: declared numeric input; `op`: `gt`; `amount`: positive finite number; `target_action_id`: existing escalate action |
| Customer messages | `primary` and `escalation`: nonempty strings |

Rule/action identifiers match `[A-Za-z][A-Za-z0-9_-]{0,63}`. Require unique action IDs. A rule describes the conjunction of its conditions, but the app only validates and displays it. Rules remain in source order; there is no runtime rule selection, priority, routing, or no-match behavior.

For `refund_duplicate_charge`, require exactly one refund action and an input named `refund_amount` of type `number`. At least one escalation boundary must name that refund action, use `refund_amount`, and have an amount equal to its `max_amount`, targeting an escalate action. Report a missing limit and missing escalation independently. `answer_question` allows human-assist/escalate actions but no refund actions; it has no financial-boundary requirement. This avoids bypassing financial validation by relabeling the intent.

## 3. Valid template and seed fixture

Template insertion uses this complete valid document. Authors can change its ID and text to create another SOP. Message strings are policy examples, not confirmations of real actions.

````markdown
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
````

## 4. Canonical mapping

Validation returns `{valid, issues, content}`. Invalid content yields `content: null`. Valid content has precisely this shape:

- `sop_id`, `title`, `owner_team`, `domain`, `intent`, `risk_level`, `max_autonomy`: front-matter strings unchanged
- `policy`: `{use_when: [...], do_not_use_when: [...]}`, from the two bullet sections, removing bullet markers and trimming surrounding whitespace
- `inputs`: the Inputs Required list
- `rules`: the Eligibility Rules list
- `actions`: the Actions list
- `boundaries`: the Boundaries object
- `customer_messages`: the Customer Messages object

Preserve YAML array order and all supported values. JSON object key order is not significant. No optional defaults or implicit data conversions: strings remain strings, booleans remain booleans, and numeric fields require finite numbers, not numeric strings or booleans.

A successful publication wraps this exact content as:

```json
{
  "sop_id": "BILL-REFUND-001",
  "version": 1,
  "published_at": "2026-01-01T12:00:00Z",
  "content": {}
}
```

Here `content` stands for the full mapped object described above, not an empty published object. `published_at` is an illustrative UTC timestamp assigned by the server. The envelope's `sop_id` must equal `content.sop_id`. Source Markdown is stored alongside the snapshot but is returned through the author draft API, not consumer detail. Historical snapshots include the same envelope as current snapshots. Publication does not alter the content to inject lifecycle state.

## 5. Demo screens and lifecycle

1. Identity selector labeled demo-only.
2. Published SOP list with domain/risk filters and author access to saved drafts.
3. Editor with template insertion, save, validate/preview, and publish actions. Publication uses the saved revision; the UI requires unsaved edits to be saved first.
4. Human detail and JSON views using the same canonical snapshot.

The lifecycle is save draft → validate/preview → publish immediately. Editing leaves the current publication unchanged; successful publication advances its integer version. Failed validation leaves the current version unchanged and reports the saved candidate failure to the author. There is no approval simulation, scheduling, notification, rule execution, or tool execution.

The normative API, error conventions, persistence behavior, verification commands, and five acceptance journeys are in `REQUIREMENTS.md`; do not infer additional scope from the former specification.
