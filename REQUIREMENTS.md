# SOP System — Trimmed Demo Requirements

## 1. Baseline and outcome

This baseline replaces the broader MVP scope. Build a local demo in Java 21 / Spring Boot, React with TypeScript, and PostgreSQL, started with Docker Compose.

Demonstrate one complete journey:

**Author structured Markdown → validate → publish immutable canonical JSON → read the same published version in human and AI views.**

`PRINCIPLES.md` defines engineering guardrails. This document defines required behavior; `spec.md` defines the supported content format and example. All three are frozen inputs during a harness run. Retained requirement IDs preserve their identity but their criteria below replace the previous baseline. Unlisted behavior is not required.

## 2. Scope and fixed decisions

- Two fixed local identities: `demo-author` and `demo-consumer`, selected visibly in the UI and sent via `X-Demo-User`. This is a labeled demo mechanism, not production authentication. The backend checks the identity on every API request.
- Authors save drafts, validate, publish, and read. Consumers read published content only. Missing/unknown identity returns `401`; a consumer attempting an author operation receives `403`.
- Publication is immediate. The server assigns consecutive positive integer versions per SOP, starting at 1. There is no authored version, effective date, approval state, or deprecation state.
- There is one editable draft per SOP. Multi-user draft concurrency is deferred. A submitted publish names the exact saved draft revision; a stale revision returns `409`. Duplicate publication of the same revision returns `409`.
- Supported risk values are `low` and `medium`, with `max_autonomy: assist` only. Unsupported values are validation errors; this demo does not implement high-risk, critical, regulatory, or autonomous workflows.
- Rules use the small structured data format in `spec.md`. No expression engine, action execution, or external business integration is required or permitted.
- Domain values are `Billing` and `Support`. Filter by domain and risk only, combined with AND; sort by `sop_id` ascending. No pagination or vocabulary management.
- Reject unknown fields in the supported schema. Validation emits errors only, with stable ordering by source path then code. No warning publication policy is needed.
- React renders human content from canonical JSON. No separate rendered-content backend endpoint is required.

## 3. Functional requirements

### FR-001 — Demo identity and authorization

Provide the two identities above, show the current identity in the UI, and enforce the permissions on direct API requests. Consumer views never return drafts. Document the mechanism as local-only.

### FR-010 — Template authoring and persistence

An author can insert the valid template from `spec.md`, edit it, save it, and reopen the saved Markdown after application restart. Preserve the source text. Show unsaved changes and save errors without discarding editor content. Saving a draft never changes published content. A saved draft has a server-assigned revision that increments on each save.

### FR-020 — Safe parsing and preview

Parse the documented YAML front matter and Markdown sections into a canonical content preview. Validate without publishing. Reject malformed YAML, duplicate keys/sections, missing required sections, unknown fields, and unsupported types with controlled issues. Machine-critical content comes from YAML, never prose.

Limit source to 64 KiB of UTF-8 bytes and YAML nesting to 20 collection levels; reject aliases and custom tags. Accept only documented section structures. Authored content, rule conditions, and action descriptions must never execute.

### FR-021 — Canonical compilation

Compile valid input into the content shape in `spec.md` without dropping supported fields or inventing data. Identical source produces structurally identical content and ordered validation issues. Server publication metadata is separate from deterministic content. Store the source with its published canonical snapshot.

### FR-030 — Structural and reference validation

Require the fields, enums, and sections specified in `spec.md`. Require unique input names, rule IDs, and action IDs. Every condition input and referenced action must exist; condition operators and value types must match the input type. Require at least one input, rule, and action. Reject malformed IDs and empty required text.

### FR-032 — Financial safety

For `intent: refund_duplicate_charge`, require exactly one `refund` action with a positive numeric `max_amount`. Require a boundary referring to that action and the numeric input `refund_amount`, with operator `gt`, an amount equal to the refund limit, and an existing `escalate` action as its target. Missing limit and missing escalation produce separate readable errors. These checks validate declared policy; the application never processes a refund or evaluates a live customer decision.

### FR-034 — Validation feedback

Return issues with `code`, `stage` (`structural` or `semantic`), `message`, and `path`. The UI distinguishes the two stages and points to the affected field/section. Return all safely discoverable issues; after a structural failure it is acceptable to omit semantic checks requiring missing data. Errors block publication on the backend regardless of client behavior. Invalid preview requests do not change any persisted state.

### FR-042 — Atomic immediate publication

An author publishes a saved draft by its revision. The backend revalidates that source and atomically stores its immutable canonical snapshot, source, assigned version, and UTC publication timestamp, then selects it as current. Enforce unique `(sop_id, version)` and unique published draft revision with database constraints. A failed, duplicate, or stale publish cannot create partial versions or change the current pointer. Concurrent publishes must not produce conflicting current versions. Successful publish returns the stored canonical snapshot.

### FR-043 — Immutable versions

Saving edits after publication changes the draft only. A subsequent successful publish creates the next version. Existing snapshots cannot be overwritten; an author can retrieve a specific published version for verification. No version-management UI is required.

### FR-045 — Preserve the previous publication

When a saved newer draft fails publication validation, retain the previous current version. Persist a failure indicator for that draft revision and show the author that publication failed and the previous version remains available. Clear that indicator on a subsequent draft save or successful publish. If nothing has been published, consumer detail returns `404`; it never substitutes draft data. Revalidating or repairing corrupted historical snapshots at read time is deferred.

### FR-050 — List and filter

List current published SOPs with ID, title, version, domain, and risk. Support domain/risk filters with the fixed semantics above; invalid filter values return `400`. Display a useful empty state. Authors can also find and reopen their saved drafts through an author-only draft list.

### FR-052 — Human view

Render policy, inputs, rules, actions, boundaries, and customer messages from the selected canonical snapshot. Show SOP identity and version. Render strings as text with raw HTML disabled; attachment and link rendering are outside scope. Labels must make clear that action and message content describes an SOP and does not report a real action executed by this app.

### FR-053 — AI JSON view

Provide the current canonical snapshot through the API and a UI JSON view. Human and JSON views use the same fetched snapshot, including identity and version, so publication during viewing does not mix versions. No consumer request mutates content or executes rules/actions.

## 4. Interface and data requirements

### IR-001 — Small documented API

Use `/api/v1`. Provide these operations:

| Method and path | Access | Contract |
|---|---|---|
| `POST /validate` | Author | `{source}` → `{valid, issues, content}`; content is null when invalid |
| `PUT /drafts/{sop_id}` | Author | `{source}` → `{sop_id, revision, source}`; stores incomplete drafts within the size limit |
| `GET /drafts` | Author | Draft summaries with revision and publication-failure indicator |
| `GET /drafts/{sop_id}` | Author | Saved source, revision, and publication-failure indicator |
| `POST /sops/{sop_id}/publish` | Author | `{revision}` → published canonical snapshot |
| `GET /sops?domain=Billing&risk=medium` | Either | Current published summaries |
| `GET /sops/{sop_id}` | Either | Current canonical snapshot |
| `GET /sops/{sop_id}/versions/{version}` | Author | Immutable historical snapshot |

Paths in this table are relative to `/api/v1`. Saving is allowed before content is valid; at publication, the parsed `sop_id` must match the draft path. Validation returns `200` even for content issues; malformed request envelopes return `400`. Successful saves and publishes return `200`.

Document request/response shapes in the application README or an API document. Generated OpenAPI and exhaustive per-endpoint contract tests are deferred. Errors use `{code, message, issues}` with `issues: []` when no field issues apply. Use `400` for malformed requests/invalid filters, `401`/`403` for identity/permission failures, `404` for absent resources, `409` for revision conflicts, `413` for oversized source, and `422` for rejected publication content. Unexpected failures return a generic `500` without internals. Limit CORS to the configured local UI origin.

### DR-001 — PostgreSQL persistence

Persist editable source/revision, publication-failure indicators, published source/snapshots, and the current-version reference. Use migrations from an empty database and a named Compose volume. Transactions and database constraints enforce publication integrity. No review, approval, audit-event, or scheduled-activation tables are required.

### DR-003 — Deterministic seed

An explicit demo profile seeds the duplicate-charge example as a saved draft. Repeated startup must not duplicate records or overwrite user edits. Demo identities are fixed configuration and need no user-management database. Use fictional data only.

## 5. Engineering and local operation

### NFR-001 — Reproducible demo

`docker compose up --build` starts exactly React, Spring Boot, and PostgreSQL with health checks and no external services. Startup waits for readiness rather than fixed sleeps. A README explains URLs, identity selection, the five acceptance journeys, shutdown, and explicit data removal. Normal restart retains drafts and published snapshots.

### NFR-020 — Content and configuration safety

Use a safe YAML parser and enforce FR-020 limits. React must not execute authored HTML or script strings. Apply authorization, validation, and size checks server-side. No real credentials in source, and no SQL, stack traces, credentials, or parser internals in API errors. No execution of authored conditions or actions.

### NFR-041 — Focused verification

Provide root-level `make verify`, `make demo`, and `make smoke` commands. `demo` starts the seeded Compose stack. `verify` runs backend build/unit tests, focused PostgreSQL integration tests, frontend tests, TypeScript checks, lint, and production build. `smoke` exercises the primary journey against Compose and exits nonzero on failure. Document any tools needed to run verification.

Tests must cover safe parsing and reference failures, missing financial limits/escalation, identity enforcement, atomic publication and immutable versions, invalid replacement, and human/JSON consistency. Use PostgreSQL for database-specific behavior. Include representative hostile YAML and HTML-string cases. No separate public `eval` command or duplicated exhaustive suite is required.

### NFR-050 — Demo usability

All required actions are discoverable in the UI, usable by keyboard, and communicate status/errors with text rather than color alone. Validation errors are understandable without server logs. A simple textarea and plain JSON viewer are sufficient.

## 6. Five acceptance journeys

| ID | Journey | Required outcome |
|---|---|---|
| AC-E2E-001 | Author selects the template, saves, validates, and publishes | Version 1 is persisted; the SOP appears in domain/risk-filtered results |
| AC-E2E-002 | Remove the refund limit and escalation boundary, then validate and attempt publish | Separate readable safety issues; publication rejected; correcting both allows publication |
| AC-E2E-003 | Open human and JSON views of a published SOP as consumer | Both use identical canonical identity/version and policy; direct consumer mutation returns `403` |
| AC-E2E-004 | Save an invalid replacement, attempt publish, then correct and publish it | Failed attempt leaves version 1 current and reports failure to the author; corrected attempt publishes version 2; version 1 remains unchanged |
| AC-E2E-005 | Start from a clean checkout with Docker, run the demo, and restart | Three healthy services, usable seed draft, successful author-to-consumer flow, retained drafts and publications |

These are public task acceptance criteria. Independent benchmark scoring happens outside the harness run.

## 7. Explicit deferrals and traceability

Deferred: review queues and approver/publisher roles; risk-tier approval/autonomy matrices; effective dates and deprecation; multi-user draft conflict handling; broad taxonomy/scope filters; audit trails; attachment support; performance SLAs; database-outage recovery scenarios; generated OpenAPI and exhaustive contract suites; production identity; external integrations; rule/action execution.

Former requirements FR-002, FR-011–012, FR-022, FR-031, IR-002–003, DR-002, NFR-002, NFR-021, NFR-030, and NFR-040 are consolidated into the retained requirements above. FR-033, FR-040–041, FR-044, FR-051, FR-060, FR-070–071, NFR-010, and NFR-031 are removed as standalone obligations; retained subsets such as filtering and safe errors are stated explicitly above. The former EV-001–014 suite and acceptance journeys are superseded by section 6 and NFR-041. There are no release-blocking TBD policy choices in this baseline; ordinary code organization and library selection remain implementation decisions.

Demo completion requires all retained requirements and the five journeys, passing applicable verification, and recorded actual outcomes in `VERIFICATION.md`. Deferred features earn no additional scope credit.
