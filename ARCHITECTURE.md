# SOP Demo — Architecture

Frozen inputs: `PRINCIPLES.md`, `REQUIREMENTS.md`, `spec.md`. All `ARC-*` decisions
trace to those IDs. This document is intentionally high-level; implementation detail
lives in `TECHNICAL_DESIGN.md`.

## System context

Three local services, one demo runtime, started with a single Compose command:

- **Authoring & policy authority (backend):** Java 21 / Spring Boot. Owns identity
  checks, safe parsing, validation, canonical compilation, atomic/immediate
  publication, and all reads. The single trusted source of behavior.
- **Consumer views (frontend):** React + TypeScript. Renders human and JSON views
  from the same canonical snapshot; never executes authored content, computes policy,
  or mutates data except through the backend.
- **Persistence (PostgreSQL):** Migrations from an empty database, a named volume,
  immutable snapshots, the current-version pointer, and draft state. Coordinates
  publication with constraints and transactions.

There are no external services, no message queues, and no distributed locking
infrastructure (`PRN-003`, `PRN-006`, `NFR-001`).

```
                 +----------------------+          +-----------------------+
  X-Demo-User    |  React/TS frontend   |  /api    |  Spring Boot backend  |
  browser ──────> |  (human + JSON view) | ───────> |  auth|parse|validate  |
                 +----------------------+   proxy   |  publish|read         |
                                                    +----------+------------+
                                                               |
                                                    +----------v------------+
                                                    |  PostgreSQL 16        |
                                                    |  drafts|publications |
                                                    +----------------------+
```

## Major components and responsibilities

### ARC-001 — Single backend authority
Supports: `FR-010..FR-053`, `NFR-020`, `PRN-004`.
Parsing, validation, compilation, authorization, publication, and querying all run in
one backend deployment. The UI is a thin client that displays canonical data and calls
the API. This prevents the UI from being a second source of truth or from being able to
bypass enforcement (backend re-checks identity and re-validates on publish).

### ARC-002 — Identity as a labeled demo mechanism
Supports: `FR-001`, `IR-001`, `PRN-004`.
Identity is carried on every request via `X-Demo-User` (`demo-author` / `demo-consumer`),
selected in the UI. The backend resolves it on every call (a `HandlerInterceptor`),
returns `401` for missing/unknown, and enforces author/consumer permissions
(`403`). The UI hides author actions for consumers but that is cosmetic — the backend is
authoritative.

### ARC-003 — Three-phase pipeline: parse → compile → validate
Supports: `FR-020`, `FR-021`, `FR-030`, `FR-032`, `FR-034`, `PRN-001`, `PRN-005`.
A document is (1) **parsed** into a neutral structure (front matter + the seven
sections) with safe-YAML guards, (2) **compiled** into the canonical content shape in
`spec.md`, and (3) **validated** (structural then semantic), producing a deterministic
issue list. The same pipeline is reused verbatim by `POST /validate` (preview) and by
publication (re-validation of the exact saved revision). Validation is pure function of
source and is independently unit-testable (`PRN-004`, `PRN-005`).

### ARC-004 — Immutable snapshots + single current pointer
Supports: `FR-042`, `FR-043`, `FR-045`, `DR-001`, `PRN-006`.
Publish inserts a new immutable publication row (canonical snapshot + source + version +
UTC timestamp) and updates one `sop_id → current version` pointer, all in one
PostgreSQL transaction. Draft edits never touch publications. `UNIQUE(sop_id, version)`
and `UNIQUE(sop_id, draft_revision)` constraints backstop the app logic; row-level
`FOR UPDATE` locks serialize concurrent publishes per `sop_id` (`PRN-006`).

### ARC-005 — Draft store is the only writable policy surface
Supports: `FR-010`, `FR-045`, `DR-001`.
One editable draft per `sop_id` holds the raw source text and a monotonically increasing
server-assigned `revision` plus a `publication_failed` indicator. Saving is allowed
before content is valid; saving never changes published state; saving clears the failure
indicator; publication of a saved revision is the only path to a new version.

### ARC-006 — Read endpoints serve only the current canonical snapshot
Supports: `FR-050`, `FR-052`, `FR-053`, `NFR-020`, `PRN-001`.
`GET /sops`, `GET /sops/{id}`, and `GET /sops/{id}/versions/{v}` all resolve to a
stored snapshot. Human and JSON views fetch the same snapshot (identity + version), so a
publication during viewing cannot mix versions. Consumers' requests are pure reads;
consumer detail never substitutes draft data and returns `404` when nothing is published.

## Important boundaries and interfaces

- **`/api/v1` REST boundary** (`IR-001`): the only interface between UI and authority.
  Fixed method/path/access table in `REQUIREMENTS.md`; error envelope
  `{code, message, issues}` and status mapping `400/401/403/404/409/413/422/500`.
- **Markdown/YAML boundary:** authored text is data. Parsed by a constrained YAML
  parser (no arbitrary tags, no aliases, no non-finite numbers, bounded nesting and byte
  size) — never executed (`PRN-004`, `NFR-020`).
- **PostgreSQL boundary:** migrations, constraints, and transactions enforce data
  integrity independent of application flow (`DR-001`, `PRN-006`).

## Key data flows

1. **Author save:** `PUT /drafts/{id} {source}` → 64 KiB check → validate envelope →
   store/replace source, `revision++`, clear `publication_failed` → `{sop_id, revision,
   source}`. (No validation of policy content required for save.)
2. **Author preview:** `POST /validate {source}` → parse → compile → validate →
   `{valid, issues, content?}` (200 even when invalid; `content` null when invalid).
3. **Author publish:** `POST /sops/{id}/publish {revision}` → lock draft row →
   revision must match current (else 409) → re-parse/compile/validate the saved source →
   invalid: persist `publication_failed=true`, 422 `issues`; valid: insert snapshot at
   `max(version)+1`, update current pointer (one transaction) → snapshot envelope.
4. **Read:** `GET /sops[?domain&risk]` → summaries of current snapshots (AND filter,
   `sop_id` asc); `GET /sops/{id}` → current snapshot envelope;
   `GET /sops/{id}/versions/{v}` (author) → that immutable snapshot.

## External dependencies

Runtime: none beyond the three services. Build/test: Maven Central, npm, and a local
Docker registry image for PostgreSQL. Java 21 toolchain, Node 22, Docker Compose.

## Security boundaries

- **Authentication:** the fixed `X-Demo-User` scheme (labeled demo-only, not production
 ). Backend checks every request.
- **Authorization:** author endpoints require `demo-author`; consumer on those is
  `403`; missing/unknown identity is `401`.
- **Content safety:** safe YAML parsing with hard limits; strings rendered as text in the
  UI (raw HTML disabled); authored conditions/actions are never executed (`PRN-004`,
  `NFR-020`).
- **Error safety:** API errors never leak SQL, stack traces, credentials, or parser
  internals (`PRN-004`, `NFR-020`).

## Failure modes

- **Oversized / malformed source:** reject with `413`/`400`/`422` and no state change
  (`FR-020`, `NFR-020`).
- **Stale or duplicate publish:** `409`; current pointer and versions untouched
  (`FR-042`, `FR-043`).
- **Invalid publish candidate:** `422`; previous current version retained; failure
  indicator surfaced to the author (`FR-045`, `AC-E2E-002/004`).
- **Concurrent publish:** row lock serializes; constraints guard unique version/revision;
  the loser fails cleanly (`PRN-006`).
- **Unexpected server error:** generic `500`, no internals (`IR-001`).

## Deployment / runtime assumptions

- `docker compose up --build` starts exactly React, Spring Boot, and PostgreSQL with
  health checks; readiness is waited on, not sleep-based. A named volume persists data so
  restarts retain drafts and publications; a documented command deletes it
  (`NFR-001`, `PRN-007`).
- A deterministic, idempotent seed (the `spec.md` duplicate-charge example as a saved
  draft) runs on an explicit demo profile and must not duplicate or overwrite user
  edits (`DR-003`, `NFR-001`).
- In this shared host the stack binds non-default host ports (backend `18080`, frontend
  `13000`, Postgres `15432`) to avoid clashing with sibling run stacks; defaults remain
  overridable via environment. This is a documented environment assumption, not a
  requirement.

## Major tradeoffs

- **JDBC + explicit SQL/transactions over heavy ORM.** Gives precise control of the
  atomic publication, the two uniqueness constraints, and the current pointer with the
  smallest dependency surface (`PRN-003`, `PRN-006`, `NFR-020`). Repositories stay thin and
  testable.
- **SnakeYAML `SafeConstructor` + token pre-scan over a bespoke YAML parser.** Reuses a
  vetted library for the hard problems (tags, types) and adds only the small guards the
  spec demands (aliases/anchors, duplicate keys, nesting, finiteness). Simpler and safer
  than writing a YAML-subset parser (`PRN-003`, `PRN-006`).
- **Vite static build served by nginx, not a dev server, in the demo container.** Mirrors
  production, keeps the frontend image small, and gives a clean `/api` same-origin proxy so
  CORS stays local (`NFR-001`, `NFR-050`).
- **Idempotent application seeder over `data.sql`.** `data.sql` would run on every
  startup; an explicit, conditional seeder satisfies DR-003's "no duplicate, no overwrite"
  and only runs under the seeded demo profile (`DR-003`).

## Unresolved questions / recorded assumptions

- **Host ports** — non-default choices are an environment adaptation to a shared host;
  requirement-neutral. (Recorded in `VERIFICATION.md` assumptions.)
- **Section heading order** — the spec requires each of the seven `##` headings exactly
  once but does not mandate order; we accept any order and reject others. (Smallest
  assumption; `PRN-005` determinism preserved.)
- **Stage classification** — `structural` = shape/allowed-values/format/ids/enums/unique-ness;
  `semantic` = cross-references + financial safety. Chosen so FR-032's "separate readable
  safety issues" and FR-034's stage split are both honored without ambiguity.

## Requirement traceability (partial)

| Requirement | Architecture |
|---|---|
| `FR-001` identity/authorization | ARC-002 |
| `FR-010` authoring/persistence | ARC-005 |
| `FR-020/021` parsing/compilation | ARC-003 |
| `FR-030/032/034` validation | ARC-003 |
| `FR-042/043/045` publication/integrity | ARC-004, ARC-005 |
| `FR-050/052/053` listing & views | ARC-006 |
| `DR-001/003` persistence/seed | ARC-004, ARC-005 |
| `NFR-001/020/041/050` operation/safety/verify | ARC-001..ARC-006 |
