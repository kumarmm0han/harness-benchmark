---
Artifact: ARCHITECTURE.md
Stage: 1 (of 6)
Date: 2026-09-05
Status: Initial
---

# SOP Demo — System Architecture

## 0. Scope reminder (frozen)

The architecture in this document is for the demo named in `REQUIREMENTS.md` and bounded by `PRINCIPLES.md`. Only the five acceptance journeys (AC-E2E-001..005) and the requirements/nfr listed therein are in scope. Deferred features (multi-tenant, multi-domain, RBAC roles beyond the two fixed identities, etc.) are out of scope.

## 1. System context

A local-only, docker-compose stack with three services:

```
+-------------+   HTTP    +-------------------+   JDBC    +-------------+
|  React+TS   | <-------- |  Spring Boot (J21) | <-------- |  PostgreSQL |
|  (nginx)    |    CORS   |  (backend)         |           |  (16)       |
|   :8075     |           |   :8080            |           |   :5432    |
+-------------+           +-------------------+           +-------------+
        ^                          ^
        | user: demo-author | demo-consumer  (via `X-Demo-User`)
```

- **React/TypeScript + Vite**: author-facing UI — identity selector, published SOP list with filters, draft editor (save/validate/publish), human detail view, JSON view. Served by nginx on a fixed local port.
- **Spring Boot / Java 21**: owns identity checks, parsing, validation, compilation, publication, and all persistence. All business rules are server-authorized.
- **PostgreSQL 16**: single data store for drafts, publications, and current-pointer. Named Compose volume. Flyway-managed schema.

No external services. No message brokers. No caches.

## 2. Major components and responsibilities

| ID | Component | Responsibility | Supports |
|----|-----------|----------------|----------|
| ARC-001 | React/TS front end (Vite + nginx) | Renders the UI; fetches from backend with `X-Demo-User`; renders canonical JSON as text-only HTML; shows unsaved / status / errors. | FR-001, FR-050, FR-052, FR-053, NFR-050, PRN-005 (render-as-text), PRN-004 (no HTML execution) |
| ARC-002 | Demo identity boundary (`X-Demo-User` filter) | Read-only identity check on every request. Enforces: missing/unknown → 401; author-only op by consumer → 403. Sets a request attribute for controllers. | FR-001, NFR-020, PRN-004 |
| ARC-003 | Content pipeline (parse → compile → validate) | Pure, deterministic transformation: `String -> {valid, issues, content}`. Same input → same output. No I/O inside. | FR-020, FR-021, FR-030, FR-032, FR-034, PRN-005 |
| ARC-004 | Structural validator | Enforces front-matter, required sections, allowed section shapes, supported types, unique names/IDs, reference integrity (condition inputs, rule action refs, escalation action/input refs). | FR-030, PRN-005 |
| ARC-005 | Financial-safety validator (FR-032) | For `intent: refund_duplicate_charge`: exactly one `refund` action with positive `max_amount`; one input `refund_amount` (number); one escalation boundary naming that action/input, using `gt`, with `amount == max_amount`, targeting an existing `escalate` action. Missing-limit and missing-escalation reported as separate issues. | FR-032, PRN-005 |
| ARC-006 | Canonical-content builder | Transforms the parsed and validated object into the exact shape from `spec.md § 4` with field trimming/order preservation. | FR-021, PRN-001, PRN-005 |
| ARC-007 | Draft service | Single editable draft per `sop_id`: save (increment revision, clear failure indicator), get, list. Persists raw source text. | FR-010, IR-001, PRN-001, PRN-006 |
| ARC-008 | Publication service | Re-validates the exact saved source; computes next integer version; inserts immutable snapshot (envelope + content + source); upserts `sop_current` pointer; clears failure indicator. All atomic in one transaction. | FR-042, FR-043, FR-045, PRN-006 |
| ARC-009 | Read API (consumer) | `GET /sops?domain=&risk=`, `GET /sops/{id}`. Reads `sop_current` + `publications`. Never returns draft data. Supports `sop_id` order, AND of filters, all matching rows. | FR-050, FR-052, FR-053, IR-001 |
| ARC-010 | Historical-version read (author) | `GET /sops/{id}/versions/{version}`. Author-only. Reads by primary key, immutable. | FR-043, IR-001 |
| ARC-011 | Seed initializer (`demo` profile) | On application startup, programmatic and idempotent: inserts the duplicate-charge example as a saved draft only when the `drafts` table is empty. Never duplicates records or overwrites user edits. | DR-003, NFR-001, PRN-007, PRN-002 |
| ARC-012 | Flyway migrations | DDL only — a single `V1__schema.sql`. Seed data is owned by ARC-011 (not by SQL migrations) so that repeated startup and restart preserve user edits without conditional-SQL bookkeeping. | DR-001, PRN-007, PRN-006 |
| ARC-013 | Docker Compose + health checks | One command `docker compose up` starts Postgres + backend + frontend. Reads readiness, not sleeps. Named volume. Safe local defaults (local Postgres credentials, no TLS). | NFR-001, PRN-007 |
| ARC-014 | Makefile + docs | Root-level `make verify`, `make demo`, `make smoke`. README explains URLs, identity, five journeys, shutdown, data deletion, and the two local-only identities. | NFR-041, NFR-001, PRN-008, PRN-007 |
| ARC-015 | CORS boundary | Single CORS rule: only the configured local UI origin may call the backend. | IR-001, NFR-020 |

## 3. Boundaries and interfaces

### 3.1 HTTP API (`/api/v1` — see IR-001)

Eight operations. All use UTF-8 JSON. Request/error envelope is fixed (`{code, message, issues}` with `issues: []` by default). Auth: `X-Demo-User` on every request.

| Method | Path | Access | Result |
|--------|------|--------|--------|
| POST | `/api/v1/validate` | Author | `{valid, issues, content}` |
| PUT | `/api/v1/drafts/{sop_id}` | Author | `{sop_id, revision, source}` |
| GET | `/api/v1/drafts` | Author | drafts list |
| GET | `/api/v1/drafts/{sop_id}` | Author | one draft |
| POST | `/api/v1/sops/{sop_id}/publish` | Author | published envelope |
| GET | `/api/v1/sops?domain=&risk=` | Either | current list |
| GET | `/api/v1/sops/{sop_id}` | Either | current snapshot |
| GET | `/api/v1/sops/{sop_id}/versions/{version}` | Author | historical snapshot |

Status semantics: 200 success; 400 malformed envelope / invalid filter; 401 missing/unknown identity; 403 consumer on author-only op; 404 absent; 409 stale or duplicate publish; 413 source > 64 KiB; 422 validation failure; 500 unexpected.

### 3.2 React ↔ Spring (CORS)

Single rule: allow origin from `app.cors-allowed-origins` (default `http://localhost:8075`), allow header `Content-Type` and `X-Demo-User`, allow methods in the API above. No wildcard origin together with credentials.

### 3.3 Spring ↔ PostgreSQL

Single JDBC datasource. Transactions from JPA (Spring Data JPA) or template. Constraints:
- `publications (sop_id, version) PRIMARY KEY` — consecutive, per-SOP.
- `publications (sop_id, draft_revision) UNIQUE` — a draft revision can be published at most once.
- `sop_current (sop_id) PRIMARY KEY, (sop_id, version) REFERENCES publications(sop_id, version)`.

Concurrency control on publish uses Postgres advisory locks keyed by `sop_id` (ordinary database coordination, not distributed locking — per PRN-006).

### 3.4 React ↔ backend

Fetch with `X-Demo-User` and `Content-Type: application/json`. Response bodies match the table in §3.1. Errors are plain text + code — no stack traces, no parser internals.

## 4. Key data flows

### 4.1 Author: save → validate → publish

1. `PUT /drafts/{sop_id}` with `{source}` → upsert row, `revision++; publish_failed_at = NULL`, return new revision. Size checked server-side first.
2. `POST /validate` with `{source}` → pipeline runs → `{valid, issues (if any), content (if valid, else null)}`. If `!valid` and `source` is a saved draft, persist `publish_failed_at` for that revision (best-effort; see §6).
3. `POST /sops/{sop_id}/publish` with `{revision}` → backend re-validates the *saved* source (never the client-sent source) → if issues, 422 with issues; if ok, transaction inserts the immutable snapshot and updates the current pointer. Response: the stored envelope.

### 4.2 Consumer: list and detail

1. `GET /sops?domain=Billing&risk=medium` → `sop_current` joined against `publications`; only `sop_id, title, version, domain, risk` returned.
2. `GET /sops/{sop_id}` → full envelope (identity, version, published_at, content).

### 4.3 Failure and recovery

- Invalid source on publish: `publications` untouched, `sop_current` untouched, `drafts.publish_failed_at` set. Author reopens draft → "last publish failed" indicator.
- Author edits (save) clears the indicator. Author corrects and publishes → version advances, indicator cleared.
- If nothing has ever been published for a `sop_id`, consumer detail returns `404` (no fallback to draft).

## 5. External dependencies

- **PostgreSQL 16** (local, Compose, named volume).
- **Spring Boot 3.5.x** (Spring Framework 6.1.x) — web, validation, data-jpa, flyway, actuator (health only).
- **SnakeYAML 2.4** (managed by Spring Boot; used directly with `SafeConstructor` to forbid custom tags and aliases).
- **Jackson** (transitive) for JSON.
- Node 18 + npm. React 18, TypeScript, Vite 5, ESLint. Node test: Vitest + React Testing Library.

No message buses, no external authn, no object storage, no third-party APIs.

## 6. Security boundaries

- **Identity**: demo-only, local, labeled as such (`X-Demo-User`). Not a secret. Not production. Missing/unknown → 401 on every endpoint. Author-only routes reject `demo-consumer` with 403 — enforced server-side, not just hidden in the UI.
- **Content**: all Markdown/YAML from authors is data. SnakeYAML is configured in **safe** mode — no `!!` tags, no aliases, no custom tags, no non-finite numbers (rejected by validator before Jackson). React renders via `dangerouslySetInnerHTML` nowhere; all user strings rendered as text (React escapes by default). HTML in prose sections stays as raw text.
- **Errors**: never leak SQL, stack traces, credentials, or parser internals. 400/409/422/413 messages reference the field or issue code, not internal state. 500 returns a generic body.
- **CORS**: single rule, single configured origin (default `http://localhost:8075`), no wildcard+credentials.
- **Size**: 64 KiB UTF-8 source limit enforced before parsing. YAML nesting limited to 20 levels (counting the root mapping/list as level 1). Rejected by the parser.
- **Publication integrity**: enforced by DB constraints (unique per-SOP version; unique per-SOP published draft revision) plus an advisory lock on `sop_id` during publish. Failed/duplicate/stale publishes are 409 or 422 and do not leave partial state.

## 7. Failure modes

| Mode | Handling |
|------|----------|
| Missing/unknown identity header | `401` on every endpoint. UI shows the reason from the body. |
| Consumer invokes author-only op | `403` with code `FORBIDDEN`. UI hides the action but the server still enforces. |
| Source > 64 KiB on save | `413` with code `SOURCE_TOO_LARGE`. Draft row unchanged. Editor content preserved (UI does not clear on error). |
| Invalid YAML / unknown keys / bad types / missing section | `POST /validate` → 200 with `valid=false`, `content=null`, issues. `POST /sops/{id}/publish` → 422 with same issues. Failed indicator set if source is a saved draft. |
| Concurrent publishes of the same SOP | Advisory lock serializes them. Second txn sees the new current pointer and the new MAX(version) and gets a distinct version. Different revisions still each succeed (constraint prevents same-revision duplicates). |
| Concurrent publish of the same draft revision (already-published) | `UNIQUE (sop_id, draft_revision)` fires → 409 `PUBLICATION_CONFLICT`. No partial rows. |
| Publish with a stale revision | Comparison `draft.revision != submitted` → 409 `STALE_REVISION` (checked before constraint). |
| Parse succeeds but a required field is missing | 422 with issues; failed indicator set if saved; not published. |
| Application restart | Flyway migrations are idempotent; seed initializer is idempotent; no state lost (named volume). |
| Backend process not yet ready | Compose healthchecks; frontend nginx retries via `proxy_next_upstream` are not needed because the frontend has no startup dependency on the backend (user action triggers fetches). Backend health endpoint available for smoke. |

## 8. Deployment / runtime assumptions

- `docker compose up --build` from a clean checkout. Postgres 16 image, Maven/Node/JRE images, all pinned.
- Ports are bind-to-`127.0.0.1`-style local defaults (Postgres 5433, backend 8080, nginx 8075). No external network.
- Data is stored in a named Compose volume `sop_demo_pgdata`. `docker compose down -v` deletes it. README documents this explicitly.
- No TLS, no secrets, no production concerns. Local-only.
- Health checks use `pg_isready`, Spring Boot's `/actuator/health`, and nginx `curl /healthz`. Compose uses `depends_on.condition: service_healthy`.

## 9. Tradeoffs

| Decision | Alternative | Rationale |
|----------|-------------|-----------|
| Spring Boot + Postgres (single DB) | In-memory DB; multi-DB | PRN-003 explicitly requires this stack; PRN-006 wants real transactions and constraints to be testable in PostgreSQL. |
| One editable draft per `sop_id` | Multi-draft per SOP | Requirement explicitly says so; simplest interpretation. |
| Advisory lock on `sop_id` | Row-level `FOR UPDATE` on a lock table | Advisory lock avoids an extra table and is a Postgres-native "ordinary" coordination. |
| React + Vite + TypeScript | Pre-built SPA, or server-rendered Java pages | PRN-003 explicitly says "React with TypeScript." Vite is the smallest build tool with no framework coupling. |
| SnakeYAML (safe mode) in Java | Jackson YAML (snakeyaml under the hood — same risk) | Both depend on snakeyaml. Use it directly with the safe constructor; validate with a custom type-visitor. |
| Flyway for DDL; programmatic (profile-gated) seed | SQL seed migration | Flyway is the standard Spring Boot tool for versioned DDL. Seed is a `@Profile("demo")` initializer guarded by "table empty" so repeated startup never duplicates records or overwrites user edits (DR-003) without conditional-SQL bookkeeping. |
| Identity via `X-Demo-User` (header) | Basic Auth, sessions, OAuth | Requirement explicitly says "labeled demo mechanism, not production authentication." Header is the minimum. |
| Human view rendered in React from canonical JSON | Server-side HTML rendering | Requirement explicitly says React renders from canonical JSON. |

## 10. Unresolved questions (and the assumption chosen)

| # | Question | Assumption |
|---|----------|------------|
| Q-1 | Should the draft list return only the caller's drafts? | `FR-050` says "author-only draft list". In this demo there is one author identity (`demo-author`). All drafts are that author's. List returns all rows in the `drafts` table (no `author` equality check). The `author` column is present for clarity but not filtered on. |
| Q-2 | When a `POST /validate` call has `source` that matches a saved draft and is invalid, should we set `publish_failed_at`? | `FR-034` says "an invalid preview request does not change any persisted state." So no — validate is read-only. Only `POST /sops/{id}/publish` sets the failed indicator (on 422). |
| Q-3 | Should the draft's `revision` start at 1 on first save? | Yes. Revision increments by 1 on every subsequent save. |
| Q-4 | Does "published_at" use the publish txn timestamp or a caller-provided clock? | Txn timestamp, UTC, `TIMESTAMPTZ`. Server-owned. |
| Q-5 | Does `GET /sops` default (no filters) return everything? | Yes — "domain and risk only" means either filter can be present; AND semantics, both optional. |
| Q-6 | Are rule/action identifiers (`R1`, `A1`) allowed to start with a digit? | Spec §2 says `[A-Za-z][A-Za-z0-9_-]{0,63}`. No leading digit. |
| Q-7 | Does `drafts` store the raw source or the canonical content? | Raw source (as authored). Requirement is explicit: "Preserve the source text." The canonical snapshot is stored per publication. |
| Q-8 | If the author publishes and then edits, does the previous draft row disappear? | No. The same `sop_id` draft row is updated in place with the new source and new revision. Historical *snapshots* are preserved in `publications`. Historical *drafts* (before the latest edit) are not preserved — the requirement says "one editable draft per SOP" and the "saved draft has a server-assigned revision that increments on each save." |
| Q-9 | Which error `code` strings to use in issue bodies? | Defined in TECHNICAL_DESIGN. Stable, UPPER_SNAKE, e.g. `STRUCTURAL_MISSING_FIELD`, `SEMANTIC_FINANCIAL_LIMIT_MISSING`. |
| Q-10 | Is the JSON view a `GET` or is it the same as `GET /sops/{id}`? | Same endpoint — the *view* (UI rendering) is different, but the API is the same. `FR-053` is a UI requirement. |
| Q-11 | Do we include the raw source in the published envelope? | No. `spec.md §4` defines the envelope fields. Source is stored alongside (in `publications.source`) but not in the envelope body. The author draft API returns source; the consumer detail API does not. |
| Q-12 | Do we need a health endpoint? | `/actuator/health` from Spring Boot Actuator (default `localhost:8080/actuator/health`). Used by Compose healthchecks and smoke. |
| Q-13 | Should a failed publish (422) return the full issue list or a summary? | Full issue list, same shape as `POST /validate`. The body for a failed publish: `{code: "VALIDATION_FAILED", message: "...", issues: [...]}`. |
| Q-14 | Is there a maximum number of versions per SOP? | No. Requirement says "consecutive positive integer versions." |
| Q-15 | Does `GET /sops/{sop_id}/versions/{version}` require author identity? | Yes, author-only (per IR-001). |

## 11. Requirement traceability

| ID | Where it is satisfied |
|----|-----------------------|
| FR-001 | ARC-002 (identity), ARC-001 (UI), IR-001 |
| FR-010 | ARC-007 (draft service) |
| FR-020 | ARC-003 (content pipeline + safe parser), ARC-001 (renders as text) |
| FR-021 | ARC-006 (canonical-content builder) |
| FR-030 | ARC-004 (structural validator) |
| FR-032 | ARC-005 (financial-safety validator) |
| FR-034 | ARC-003 (validator output), ARC-014 (error envelope) |
| FR-042 | ARC-008 (publication service), ARC-012 (constraints) |
| FR-043 | ARC-008 + ARC-010 + ARC-012 |
| FR-045 | ARC-007 (failure indicator), ARC-009 (consumer 404) |
| FR-050 | ARC-009 (read API), ARC-007 (draft list) |
| FR-052 | ARC-001 (human view), ARC-006 (content shape) |
| FR-053 | ARC-001 (JSON view), ARC-009 (read API) |
| IR-001 | §3.1 table + ARC-015 (CORS) |
| DR-001 | ARC-012 (migrations), ARC-008 (transactions) |
| DR-003 | ARC-011 (idempotent seed initializer) |
| NFR-001 | ARC-013 (Compose), ARC-014 (Makefile + README) |
| NFR-020 | ARC-015 (CORS), ARC-002 (identity), ARC-003 (safe parser) |
| NFR-041 | ARC-014 (make verify / demo / smoke) |
| NFR-050 | ARC-001 (UI) |
| PRN-001 | ARC-007, ARC-008 (same canonical snapshot both views) |
| PRN-002 | All ARC-* (scope discipline) |
| PRN-003 | ARC-013 (Compose stack) |
| PRN-004 | ARC-002, ARC-003, ARC-015 |
| PRN-005 | ARC-003 (deterministic), ARC-004, ARC-005 |
| PRN-006 | ARC-008, ARC-012 |
| PRN-007 | ARC-013, ARC-014 |
| PRN-008 | ARC-014 (smoke + tests), §10 Qs |
| PRN-009 | The lifecycle in this file + TASKS.md + VERIFICATION.md |
