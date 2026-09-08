# SOP Demo — Verification

Frozen inputs: `PRINCIPLES.md`, `REQUIREMENTS.md`, `spec.md`. This record reports
**actual** command outcomes, requirement coverage, principle adherence, and the
material assumptions/limitations. All commands below were run from the repository root
on the same host and model as the implementation.

## 1. Command outcomes (actual)

### `make verify`  — exit 0 (ALL GREEN)

| Stage | Command (as invoked) | Outcome |
|---|---|---|
| Backend unit | `(cd backend && mvn -q -B test)` | **57 tests, 0 failures** — SafeYaml 10, ParsingService 9, FinancialSafety 8, StructuralValidator 10, ReferenceValidator 8, ValidationService 6, CompileService 5, HealthController 1. `BUILD SUCCESS`. |
| Backend integration | `scripts/run-its.sh` → throwaway `postgres:16-alpine` on host port 15433, `SPRING_DATASOURCE_*` set, `mvn -f backend test -Dtest='*IT'` | **20 tests, PASSED** — DraftApiIT 7, SeedIT 3, PublicationIT 10. Container auto-removed after the run. |
| Frontend | `(cd frontend && npm ci && npm run lint && npm run typecheck && npm run test && npm run build)` | lint **0 problems**; `tsc --noEmit` clean; **27 tests passed** (api 4, template 2, IdentitySelector 3, SopDetail 4, SopList 7, Editor 7); `vite build` → **37 modules**. |

### `make smoke`  — exit 0 (SMOKE: PASSED, 12/12 assertions)

`make clean` → `docker compose up --build --wait` (db/backend/frontend all **healthy**) →
`scripts/smoke.sh` against the live stack:

| # | Assertion (acceptance) | Result |
|---|---|---|
| 0 | backend `/healthz` → 200 | ok |
| 1 | author reads seeded draft (`revision ≥ 1`, no failed marker) | ok |
| 2 | author `POST /validate` → `valid` (AC-E2E-001) | ok |
| 3 | author `POST /sops/{id}/publish` → canonical **version 1** (AC-E2E-001) | ok |
| 4 | `GET /sops?domain=Billing&risk=medium` lists the SOP v1 (FR-050) | ok |
| 4b | `GET /sops?domain=Support` does **not** list it (AND semantics) | ok |
| 4c | `GET /sops?domain=Hacking` → **400** (invalid filter, IR-001) | ok |
| 5 | consumer `GET /sops/{id}` → same `sop_id`, version 1, policy (AC-E2E-003) | ok |
| 6 | consumer `POST /validate` → **403** (AC-E2E-003) | ok |
| 7 | request with no `X-Demo-User` → **401** (FR-001) | ok |
| 8 | author `GET /sops/{id}/versions/1` → immutable (FR-043) | ok |
| 9 | consumer `GET /sops/{id}/versions/1` → **403** (FR-001) | ok |

### `make demo`  — db, backend, frontend all **healthy** (AC-E2E-005)

`docker compose up --build --wait` waited on readiness (no fixed sleeps); the seeded
`BILL-REFUND-001` draft was present and publishable (the smoke run above). Data persists
across restarts in the named volume `sopdemo_sopdemo_pgdata`; `make clean` removes it.

## 2. Requirement coverage

| Requirement | Architecture | Design | Task(s) | Code | Tests | Status |
|---|---|---|---|---|---|---|
| FR-001 identity/authorization | ARC-002 | DES-010 | 004, 007, 010 | `IdentityFilter`, `ApiClient.setIdentity` | DraftApiIT (401/403); smoke #6,7,9 | PASS |
| FR-010 authoring/persistence | ARC-005 | DES-008 | 004, 008 | `DraftStore`, `Editor` | DraftApiIT; Editor tests (dirty guard, retain-on-error) | PASS |
| FR-020 safe parsing/preview | ARC-003 | DES-002 | 002 | `SafeYaml`, `ParsingService` | SafeYamlTest, ParsingServiceTest (hostile YAML) | PASS |
| FR-021 canonical compilation | ARC-003 | DES-003 | 002 | `CompileService` | CompileServiceTest; template guard | PASS |
| FR-030 structural/ref validation | ARC-003 | DES-004/005 | 003 | `Structural/Reference` validators | StructuralValidatorTest, ReferenceValidatorTest | PASS |
| FR-032 financial safety | ARC-003 | DES-006 | 003 | `FinancialSafety` | FinancialSafetyTest (separate limit/escalation) | PASS |
| FR-034 validation feedback | ARC-003 | DES-004 | 003, 008 | `ValidationService`, issues | Editor structural+semantic test | PASS |
| FR-042 atomic publish | ARC-004 | DES-008 | 005 | `PublicationService` (txn, `FOR UPDATE`) | PublicationIT (concurrent/winner) | PASS |
| FR-043 immutable versions | ARC-004 | DES-008 | 005 | version/revision uniqueness | PublicationIT; smoke #8 | PASS |
| FR-045 preserve previous | ARC-004/005 | DES-008 | 005, 008 | failed indicator + `Editor` banner | PublicationIT (retains v1); Editor failed-pub test | PASS |
| FR-050 list/filter | ARC-006 | DES-013 | 007 | `SopList` | SopList tests (AND, role gating, empty, error) | PASS |
| FR-052 human view | ARC-006 | DES-014 | 009 | `SopDetail` human | SopDetail tests | PASS |
| FR-053 AI JSON view | ARC-006 | DES-014 | 009 | `SopDetail` JSON (same snapshot) | SopDetail same-snapshot test | PASS |
| IR-001 small API + errors | ARC-001 | DES-009/011 | 004, 005 | `*Controller`, `ApiErrors` | DraftApiIT, PublicationIT, smoke | PASS |
| DR-001 PostgreSQL persistence | ARC-004 | DES-007 | 004 | JDBC + Flyway `V1` | ITs (real PG) | PASS |
| DR-003 deterministic seed | ARC-005 | DES-015 | 004 | guarded runner | SeedIT (idempotent) | PASS |
| NFR-001 reproducible demo | — | DES-012/017 | 006, 010 | compose + Makefile | `make demo`/`smoke` | PASS |
| NFR-020 content/config safety | ARC-001 | DES-002/011 | 002, 007, 009 | safe parser; text rendering | template guard; HTML-as-text test | PASS |
| NFR-041 focused verification | — | DES-016 | 010 | `make verify`/`smoke` | both green | PASS |
| NFR-050 demo usability | — | DES-013/014 | 007, 008, 009 | native controls, text status | keyboard/role-gating tests | PASS |

All 21 requirements/FR/DR/NFR are **PASS**.

## 3. Principle adherence

- **PRN-001 one canonical source** — one `Content` record is compiled and stored once; both
  `SopDetail` views read the same fetched snapshot. No separately editable representations.
- **PRN-002 complete the small journey** — template → save → validate → publish → human/JSON
  read all implemented; only low/medium + `assist`; unsupported values rejected.
- **PRN-003 three-service runtime** — exactly React/Spring Boot/PostgreSQL in Compose; parsing,
  validation, publication, and reads all in the backend; no external services.
- **PRN-004 backend authority** — `IdentityFilter` + `PublicationService` re-validate and enforce
  on the server; the UI only hides author controls for the consumer.
- **PRN-005 deterministic validation** — same source ⇒ same content/issues (determinism tests);
  financial checks centralized in `FinancialSafety` with independently distinct codes.
- **PRN-006 atomic publication** — single transaction, `FOR UPDATE` row lock, `UNIQUE(sop_id,version)`
  and `UNIQUE(sop_id,draft_revision)`; loser concurrent publish fails cleanly (PublicationIT).
- **PRN-007 reproducible delivery** — one Compose command, readiness via healthcheck + `up --wait`,
  idempotent seed, named volume, documented `down`/`clean`.
- **PRN-008 evidence proportional to scope** — this file, real outcomes, 106 tests (57 unit + 20 IT +
  27 frontend), 12 smoke assertions; no weakened/deleted tests.
- **PRN-009 traceable implementation** — TASKS.md carried IN_PROGRESS → COMPLETED per task with
  verification; each task committed + pushed with its ID (TASK-001…010) in order.

## 4. Design-to-code consistency

No material deviations. Two recorded assumptions from ARCHITECTURE.md hold:
section headings need each to appear once but **order is not mandated** (any order accepted,
others rejected); `stage` = structural | semantic split. One environment adaptation (documented):
non-default host ports 18080/13000/15432 to avoid clashing with sibling run stacks on a shared
host — requirement-neutral, overridable. Frontend `@testing-library/react` was pinned to `^15.0.7`
(15.2+ is not present in this environment's npm registry mirror).

## 5. Known limitations (real)

- Specific-version *display* is author-only via the API (`GET /sops/{id}/versions/{v}`, verified
  in smoke #8/#9); the UI detail shows the current snapshot. This matches IR-001 (no version-list
  endpoint) and FR-052/053 scope.
- The `smoke` re-runs from a clean volume (`make clean → demo → smoke.sh`) so a repeated publish
  starts at version 1; against an existing volume the same-revision publish would correctly `409`.
- Verification was run on this shared host (existing unrelated containers untouched; the stack uses
  its own non-default ports and project name).

## 6. Assumptions

1. **Non-default host ports** (18080/13000/15432) — environment adaptation for a shared host;
   defaults remain overridable via `SOPDEMO_*_HOST_PORT`. Requirement-neutral.
2. **Section order** — `spec.md` requires each of the seven `##` headings once but not their order;
   we accept any order and reject others (smallest assumption; determinism preserved).
3. **Stage split** — structural vs semantic chosen so FR-032's "separate readable safety issues"
   and FR-034's two-stage requirement are both unambiguous.
4. **Frontend test framework** — `@testing-library/react` 15.0.x because 15.2+ is unavailable in
   this npm registry mirror; behavior is otherwise standard Testing Library.

## 7. How to re-run

```sh
make verify        # backend unit + integration (throwaway PG) + frontend test/lint/typecheck/build
make smoke         # clean -> demo (compose up --build --wait) -> primary author->consumer journey
make demo          # bring up the seeded stack; open http://localhost:13000
make down          # stop (keep data)   |   make clean        # stop + remove the data volume
```

Tools needed: `docker` + Compose v2, `make`, `bash`, `curl` + `jq` (smoke), `maven` + JDK 21
(backend), `node` + `npm` (frontend + its Docker build).
