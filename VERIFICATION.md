# VERIFICATION

This record verifies the delivered demo against the frozen inputs
(`PRINCIPLES.md`, `REQUIREMENTS.md`, `spec.md`), per task TASK-015.
All evidence below is from the final state of the branch (`make verify` and
`make smoke` exit 0; acceptance run 18/18 pass).

## 1. Environment

- Local machine, Docker 29 + Compose; `postgres:16-alpine`,
  Java 21 / Spring Boot 3.4.5 / Maven; Node 22 / Vite 5 / React 18 /
  TypeScript 5.6; Vitest + React Testing Library + jsdom.
- Stack: `make demo` → 3 healthy services (db, backend, frontend).
- UI origin http://localhost:3010 (nginx serves the SPA and proxies `/api`
  to the backend); backend directly at http://localhost:8080.
- Fresh data state for the acceptance run: `make clean-data` + `make demo`
  (seeded draft present, nothing published).

## 2. Command evidence (final state)

### `make verify` — exit 0
- Backend: `cd backend && mvn -q test` → **83 tests run, 0 failures, 0
  errors, 0 skipped** (Surefire summary). Testclasses:
  - content engine: `YamlSafeTest` (8), `SectionParserTest` (9),
    `ContentValidationTest` (23) — safe parsing, structural + semantic
    validation, canonical compilation/sort, 413 limits
  - `ValidateApiTest` (7) — endpoint contract, 422/400/401 paths
  - `DraftApiTest` (10) — revisioning, `publish_failed` flag, 404/413/403/401
  - `IdentityAndErrorApiTest` (10) — 401/403, normalized 404/405, generic
    500 without internals, error-shape `issues: []`, CORS
  - `PublicationApiTest` (9) — publish happy path, 422 with separate issues,
    sop_id mismatch 422, stale 409, duplicate 409, **2-thread concurrent
    publish (exactly one wins)**, immutable v1 after v2, 404
  - `ReadApiTest` (5) — list + AND filters + ORDER BY, invalid filter 400,
    current snapshot shape, consumer 404 when unpublished
  - `SeedApiTest` (2) — seed creates exactly one draft, reseed idempotent,
    never auto-publishes
  (Integration tests run on Testcontainers PostgreSQL 16.)
- Frontend: `tsc --noEmit` clean; **Vitest 13/13 pass**; ESLint 0
  findings; `vite build` → 38 modules, 164 kB JS (51.96 kB gzip).

### `make smoke` — exit 0 (recorded output)
```
smoke: UI origin reachable
smoke: validate: valid (0 issues)
smoke: draft saved (revision=1)
smoke: published sop=SMOKE-001 version=1 (envelope sop_id == content sop_id)
smoke: consumer list shows SMOKE-001 v1 (domain/risk filter matched)
smoke: consumer detail: version=1 title=Smoke Check Refund
smoke: author read historical version 1
smoke: consumer version-history denied (403)
smoke: missing identity denied (401)
smoke: consumer draft save denied (403)
SMOKE PASS: author validate -> save (rev 1) -> publish v1 -> consumer
            list/detail; history author-only; identity enforced.
```

## 3. Acceptance journeys (AC-E2E-001 … 005) — run live 18/18 pass

Executed as HTTP calls through the UI origin against the running stack
(after `make clean-data` + `make demo`); the final full run:

### AC-E2E-001 — author: save → validate → publish → version 1
- `POST /api/v1/validate` (seed template source): `valid=true`, `issues=[]` — ok
- `PUT /api/v1/drafts/BILL-REFUND-001`: saved, revision reported — ok
- `POST /api/v1/sops/BILL-REFUND-001/publish {revision}`: **200, version 1**,
  envelope `sop_id` == `content.sop_id` — ok
- consumer `GET /sops?domain=Billing&risk=medium`: lists
  `BILL-REFUND-001 v1` — ok
- consumer `GET /sops/BILL-REFUND-001`: human fields + canonical content for
  the same version — ok

### AC-E2E-002 — two safety issues (missing limit + missing escalation)
- mutated draft (no `max_amount`, `escalation: []`): validate →
  `valid=false` with exactly two separate semantic issues:
  - `refund-limit-missing` (path `actions`)
  - `refund-escalation-missing` (path `boundaries.escalation`) — ok
- publish → **422 rejected** — ok
- draft list shows `publish_failed: true` — ok
- consumer still sees **version 1** (previous version unchanged) — ok

### AC-E2E-003 — consumer: human and JSON views of the same snapshot
- one `GET /sops/{id}` snapshot feeds both views; human fields (title,
  customer message) and the canonical JSON present in the identical object — ok
- snapshot stable: sha256 over the canonical content
  `07373b54…130afe67` repeated identically across reads — ok
- (Frontend) both tabs render that one fetched object — covered by
  `App.test.tsx` "human and JSON views render the same fetched snapshot";
  XSS sample (`<script>` in an authored description) renders inert as text —
  covered by tests; all UI text is plain text, no HTML string rendering.

### AC-E2E-004 — failed replacement keeps version 1; fix → version 2
- rejected publish above left v1 current — ok
- fixed draft saved (revision 4) → publish → **200, version 2** — ok
- consumer current version now 2 — ok
- consumer `GET …/versions/1` → **403** (history is author-only) — ok
- author `GET …/versions/1` returns v1 whose content sha256 equals the v1
  snapshot published earlier (`07373b54…130afe67`) — **byte-identical
  immutable history** — ok

### AC-E2E-005 — clean checkout → run → restart
- `make stop` + `make demo` (fresh image builds from source): services
  healthy — ok
- exactly **one** `BILL-REFUND-001` draft after restart (seed not
  duplicated) — ok
- published current version **2** retained across restart (data volume) — ok
- consumer list reachable after restart (1 SOP) — ok

## 4. Traceability (requirement → evidence)

| Requirement | Evidence |
|---|---|
| AC-E2E-001 | §3 AC-001 (live); frontend editor journey `App.test.tsx` "template insert, save, validate, publish"; `DraftApiTest`, `PublicationApiTest` |
| AC-E2E-002 | §3 AC-002 (live 422 + two codes); `PublicationApiTest` rejected publish; `ContentValidationTest` semantic rules |
| AC-E2E-003 | §3 AC-003 (live); `ReadApiTest` snapshot shape; `App.test.tsx` two-pane + XSS tests |
| AC-E2E-004 | §3 AC-004 (live, incl. sha256-identical v1); `PublicationApiTest` duplicate/stale + immutable v1 + concurrent publish |
| AC-E2E-005 | §3 AC-005 (live restart from clean state); `SeedApiTest` idempotence |
| FR-001 identity on every request | `IdentityAndErrorApiTest` (401 unknown/missing, 403 consumer mutations, CORS); smoke steps; `IdentityFilter` applied to all endpoints |
| FR-005 content rules (structure, types, refs, enums, IDs, refs, limits) | `ContentValidationTest` (23), `SectionParserTest`, `YamlSafeTest`; `ValidateApiTest` |
| FR-010 save allowed before valid; 409 on publish; failed badge | `DraftApiTest` (save invalid content OK; `publish_failed` flag); UI `App.test.tsx` (badge + failed save keeps content) |
| FR-034 separate issues w/ stage+code+message+path; no content when invalid | `PublicationApiTest` 422 two-code case; `ValidateApiTest`; `App.test.tsx` FR-034 test |
| FR-043 author-only history; consumers excluded | live 403 in AC-004; `PublicationApiTest`/`ReadApiTest` consumer exclusions; `App.test.tsx` picker test |
| FR-045 drafts never substitute | `ReadApiTest` 404 unpublished (consumer); AC-002 (previous version retained); `App.test.tsx` 404 text test |
| FR-050 list + AND filters + 400 | `ReadApiTest`; live list assertions; `App.test.tsx` filter test |
| FR-051/052 snapshot identity + version stamp | `PublicationApiTest` envelope; AC-003/AC-004 sha256 identity |
| FR-053 human+JSON same snapshot | AC-003; `App.test.tsx` |
| NFR-014 safe YAML | `YamlSafeTest` (aliases/tags/binary/depth/size rejection) |
| NFR-020 no leaks | `IdentityAndErrorApiTest` generic 500 `internal-error` w/o internals; messages audited in `Codes`/validators |
| NFR-041 verification via Docker | §2 (`make verify`, `make smoke`); `AbstractPostgresSpringTest` Testcontainers |
| NFR-001 labeled local-only demo | README identity selector; UI footer label `App.test.tsx`; fixed identities `DemoIdentities` |
| IR-001 API contract (base path, snake_case, error shape, 200 saves/publish) | all API tests assert status + shape; `ApiError`/`ApiExceptionHandler`; README API reference |
| DR-003 deterministic seed | `SeedApiTest`; AC-005 exactly-one-draft |
| PRN-005 stable codes/messages | `Codes.java` constants; issue-code list asserted in tests and README |
| PRN-007 data deletion explicit | `make clean-data` used in §3 final step; README documents it |

## 5. Conformance to PRINCIPLES.md

- **YAGNI / no speculative abstractions**: single-purpose packages
  (`content`, `drafts`, `publish`, `sop`, `identity`, `api`); no ORM beyond
  the JDBC layer needed; no plugin systems, no config-driven extensions, no
  unused abstractions. (Self-review of final code; `ARCHITECTURE.md` §3.)
- **No placeholder/fake completion**: every TASK in `TASKS.md` is
  COMPLETED with concrete outcome evidence; no `TODO`/`FIXME` left in
  production code paths exercised by the tests (searched during review).
- **Traceability**: all FR/AC/NFR/IR/DR/PRN/ARC/DES/TASK IDs cross-referenced
  above; `TASKS.md` is the live tracker.
- **Testing before "done"**: unit (content engine), integration
  (PostgreSQL-backed API), frontend component, E2E smoke, and the five
  acceptance journeys all executed and green in §2–§3.
- **No weakening/deletion of requirements or tests**: no test or assertion
  was removed to pass; two defects found during TASK-014 were fixed in
  production code (405 mapping) with new tests added, never by relaxing
  expectations.
- **Safety**: authored refund rules/actions are represented data only — the
  demo contains **no execution path** for any authored business action
  (no refunds, no external calls); UI labels state this explicitly.

## 6. How to reproduce

```
git clone <repo> && cd <repo>
make demo            # 3 healthy services (Docker; Postgres on host :5432)
# open http://localhost:3010 and use the demo identity selector
make verify          # backend 83 tests + frontend 13 tests + typecheck + lint + build
make smoke           # primary journey over HTTP (needs the stack)
make stop            # keep data
make clean-data      # explicit data removal
```
