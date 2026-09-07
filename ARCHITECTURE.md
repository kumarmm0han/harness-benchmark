# SOP Demo — Architecture

Frozen inputs: `PRINCIPLES.md` (PRN-001…PRN-009), `REQUIREMENTS.md` (FR/IR/DR/NFR/AC), `spec.md` (content contract).
All decisions below are traceable to those IDs.

## System context

A local, single-tenant demo application. Two fixed demo identities (`demo-author`, `demo-consumer`) are selected in a React UI and sent as the `X-Demo-User` header (IR-001, FR-001). A Spring Boot backend owns parsing, validation, publication, and querying. PostgreSQL persists drafts, published canonical snapshots, versions, and current pointers (DR-001). A three-service Docker Compose stack is the only runtime (PRN-003, NFR-001).

There is **no external service dependency**: no auth provider, no message broker, no object storage, no rule-execution engine. Authored rules/actions are data only and are never executed (PRN-004, NFR-020).

## Components and responsibilities

### ARC-001 — React UI (single service)
Supports: FR-001, FR-010, FR-034, FR-050, FR-052, FR-053, NFR-050
A React + TypeScript single-page app served as static files with an nginx reverse proxy to the API. Responsibilities: identity selector (labeled demo-only), publisher list with domain/risk filters, author draft list, Markdown editor (textarea, template insertion, unsaved-change indicator, save/validate/publish), human detail view, and JSON view of the same fetched snapshot. The UI performs no business validation and cannot bypass backend enforcement (PRN-004). Authored strings are rendered as text; no `dangerouslySetInnerHTML`.

### ARC-002 — Backend REST API layer (single Spring Boot deployment)
Supports: IR-001, FR-001, NFR-020
A thin controller layer under `/api/v1` exposing exactly the eight documented operations. Responsibilities: identity resolution from `X-Demo-User` (401 if missing/unknown), authorization (403 for consumers on author operations), request envelope validation (400), source size enforcement (413), and a single normalized error shape `{code, message, issues}`. No business rules live here.

### ARC-003 — Content engine (parse → structure-validate → semantic-validate → compile)
Supports: FR-020, FR-021, FR-030, FR-032, FR-034, PRN-001, PRN-004, PRN-005, NFR-020
A pure, stateless pipeline that turns a Markdown source string into either (a) a set of stable, deterministically ordered issues, or (b) a structurally validated canonical content map. It includes:
- a safe YAML loader (aliases rejected, custom tags rejected, 20-level nesting limit, 64 KiB source limit);
- structural validation (front matter fields/enums, section structure, per-structure fields, unknown-field rejection, duplicate keys, types);
- semantic validation (unique IDs, reference integrity, condition/operator/type consistency, ≥1 input/rule/action);
- a central, independently testable financial-safety module (FR-032) that checks refund limit and escalation boundary independently.
Deterministic: identical source yields identical content and issue order (path, then code) (PRN-005). Nothing here touches the network or database, which keeps it unit-testable.

### ARC-004 — Draft persistence and revisioning
Supports: FR-010, FR-045, DR-001, PRN-006
One editable draft per SOP in PostgreSQL. Saves are a single atomic upsert that increments the server-owned `revision` on every save and clears the publication-failure indicator (FR-010, FR-045). Saving never touches publication tables. Draft source is preserved verbatim.

### ARC-005 — Publication service (atomic, versioned)
Supports: FR-042, FR-043, FR-045, PRN-001, PRN-006, DR-001
A single transactional service that: revalidates the exact saved draft revision (409 stale, 422 invalid content with the failure indicator persisted), enforces that the parsed `sop_id` matches the path, assigns the next integer version via row-level database coordination (ordinary `SELECT … FOR UPDATE` + unique constraints, not distributed locks), inserts the immutable snapshot row (source + canonical content + version + UTC timestamp + source revision), updates the single current pointer, and clears the failure indicator. Duplicate/stale/concurrent publishes are rejected by constraints and coordination (FR-042). Published snapshots are never updated or deleted by application code (FR-043).

### ARC-006 — Read model for consumers and authors
Supports: FR-050, FR-052, FR-053, FR-043
Queries the current pointer + snapshot tables. Both the human view and the JSON view read the same row fetched once per request; publication never mutates existing snapshots, so a reading session cannot mix versions (FR-053). Consumers cannot see drafts (FR-001). Authors can retrieve historical versions (FR-043).

### ARC-007 — Seeding (demo profile)
Supports: DR-003, PRN-007
When the `demo` Spring profile is active, an idempotent startup step inserts the spec.md duplicate-charge example as a *saved draft* using `ON CONFLICT DO NOTHING` semantics, so restarts never duplicate records nor overwrite user edits. Identities are fixed configuration. All seed data is fictional (spec.md).

### ARC-008 — Compose runtime
Supports: NFR-001, PRN-003, PRN-007
Exactly three services: `postgres:16-alpine` with a named volume and healthcheck; the Spring Boot image with Flyway migrations and a healthcheck; the frontend static server (nginx) with a healthcheck and proxy to the backend. Readiness is wait-based (`depends_on.condition: service_healthy`), never fixed sleeps.

## Boundaries and interfaces

- **UI ↔ API**: JSON over HTTP, `/api/v1`, `X-Demo-User` header. The documented endpoint table (IR-001) is the complete interface.
- **API ↔ content engine**: pure functions (source string in; issues/content out). No shared mutable state, no DB access inside the engine (keeps PRN-005 determinism testable in isolation).
- **API/engine ↔ persistence**: SQL via Spring JDBC with named-parameter templates; no ORM. Tables: `sops` (current pointer), `sop_versions` (immutable snapshots + unique `(sop_id, version)` and `(sop_id, source_revision)`), `drafts` (source, revision, failure indicator).
- **Backend ↔ PostgreSQL**: TCP loopback inside the Compose network; no other network peers.

## Key data flows

1. **Author save**: UI → `PUT /drafts/{sop_id}` → size check (413) → upsert `drafts`, `revision+1`, clear failure flag → `{sop_id, revision, source}`.
2. **Author validate**: UI → `POST /validate` → size check → parse → structural → semantic → `{valid, issues, content}`; no state written.
3. **Author publish**: UI → `POST /sops/{sop_id}/publish {revision}` → transaction: draft read/lock → revision match (409) → revalidate (422 + failure flag) → sop_id match → version assignment → snapshot insert → current pointer update → failure flag cleared → envelope returned.
4. **Consumer read**: UI → `GET /sops` (filters) / `GET /sops/{sop_id}` → snapshot rows only; 404 if nothing published for that SOP.
5. **Author history**: UI → `GET /sops/{sop_id}/versions/{n}` → immutable snapshot by `(sop_id, version)`.

## External dependencies

Java 21 (Temurin), Spring Boot 3, SnakeYAML (safe loader), Flyway, PostgreSQL 16, Node 20 (build only), React 18, Vite. All are consumed only to serve a concrete demo purpose (PRN-003). No secrets or credentials in source (NFR-020); the Postgres credentials are local-only Compose placeholders documented as demo defaults.

## Security boundaries

- **Identity**: `X-Demo-User` header validated against the two configured identities on *every* API request in a single backend interceptor (PRN-004). Missing/unknown → 401; consumer on author operation → 403 (FR-001). The UI cannot substitute backend checks.
- **Content as data**: Markdown/YAML is parsed with a strict safe loader (aliases/custom tags rejected, finite numbers only, known keys only) and never executed; all strings rendered as text in the UI (PRN-004, NFR-020).
- **Errors**: a single exception handler maps failures to the documented status codes with `{code, message, issues}`; no SQL, stack traces, or parser internals leak to clients (IR-001, NFR-020).
- **Transport**: loopback-only Compose network; CORS restricted to the one configured local UI origin (IR-001).

## Failure modes

- **Invalid source at validate/save**: controlled issues returned or draft stored verbatim (saving is allowed before content is valid) (IR-001, FR-010).
- **Stale/duplicate/concurrent publish**: 409 via revision check, unique `(sop_id, source_revision)`, and unique `(sop_id, version)` under per-SOP row lock (FR-042).
- **Failed publication validation**: 422 + failure indicator persisted on the draft revision; current pointer untouched (FR-045).
- **Oversized source**: 413 before parsing (FR-020).
- **PostgreSQL unavailable**: backend fails its healthcheck; Compose depends on `service_healthy`, so no half-up stack (NFR-001).
- **YAML pathologies** (aliases, deep nesting, non-finite numbers, custom tags, duplicates): rejected with structural issues, never executed.

## Deployment / runtime assumptions

- A host with Docker + Docker Compose and Make (NFR-001, NFR-041).
- Single-machine local demo; no TLS, no multi-instance, no scaling (explicit demo scope, PRN-002).
- Data lives in a named Compose volume; explicit removal is documented (PRN-007).

## Major tradeoffs

- **JDBC over an ORM**: persistence is a few tables and a handful of statements; an ORM would add lifecycle/second-level-cache behavior irrelevant to this scope and would obscure the constraint-based integrity we must demonstrate (PRN-003, DR-001).
- **Map-based canonical content** (validated `LinkedHashMap`) instead of a generated POJO tree: the spec says "no optional defaults or implicit data conversions" and "preserve all supported values"; passing the validated structure straight through is the simplest lossless representation and keeps key order deterministic for tests.
- **nginx static server + proxy** instead of bundling the UI into Spring Boot: keeps the three services distinct and the frontend build independent (PRN-003).
- **Row-level `FOR UPDATE` + unique constraints** instead of advisory locks/distributed locking: "ordinary database coordination" per PRN-006.
- **No router library in React**: the app has four views and a small view-state switch; a router is speculative for this scope (PRN-002).

## Unresolved questions / assumptions (recorded per benchmark rule)

1. **Section order**: `spec.md` says the seven H2 headings each appear exactly once; it lists them in a specific order. **Assumption**: the heading order must match the listed order. (Structural issue otherwise.)
2. **Publish mismatch status**: `spec.md` says parsed `sop_id` must equal the draft path; it does not fix the status when they differ. **Assumption**: `422` (rejected publication content), consistent with IR-001's "422 for rejected publication content".
3. **Healthcheck semantics**: "services with health checks" (NFR-001) does not mandate an HTTP probe; the backend uses an HTTP probe from inside the container (curl is installed for that one purpose), the frontend and database use their standard probes.
4. **Node version for the UI build**: 20 LTS (Vite baseline); not fixed by any requirement.

## Requirement traceability (summary)

| Requirement area | Architecture item |
|---|---|
| FR-001 / NFR-050 identity UI | ARC-001, ARC-002 |
| FR-010 drafts | ARC-001, ARC-004 |
| FR-020/021/030/032/034 content engine | ARC-003 |
| FR-042/043/045 publication | ARC-005 |
| FR-050/052/053 reads | ARC-001, ARC-002, ARC-006 |
| IR-001 API | ARC-002, ARC-006 |
| DR-001/DR-003 persistence & seed | ARC-004, ARC-005, ARC-007 |
| NFR-001/020/041/050 | ARC-001, ARC-002, ARC-003, ARC-008 |
| PRN-001 canonical single source | ARC-003, ARC-005 |
| PRN-002 small scope | all (scope discipline) |
| PRN-003 three services | ARC-008 |
| PRN-004 backend authority / untrusted content | ARC-002, ARC-003 |
| PRN-005 deterministic validation | ARC-003 |
| PRN-006 atomic publication | ARC-005 |
| PRN-007 reproducible delivery | ARC-007, ARC-008 |
| PRN-008 evidence | see TASKS.md / VERIFICATION.md |
| PRN-009 traceability | this document, TASKS.md |
