# TASKS — SOP Demo Implementation Tracker

Working branch: `pi-01` on `origin` (`https://github.com/kumarmm0han/harness-benchmark.git`).
Each task: implement → verify → mark `COMPLETED` here → commit (with task ID) → push → confirm push.

Statuses: `TODO`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`.

---

### TASK-001 — Architecture, technical design, and task plan
Status: COMPLETED
Implements: PRN-009, PRN-005
Depends on: —
Work:
- Stage 0: index `PRINCIPLES.md`, `REQUIREMENTS.md`, `spec.md`, environment, repo state
- write `ARCHITECTURE.md` (ARC-001…008, traceability)
- write `TECHNICAL_DESIGN.md` (DES-1xx…4xx)
- write `TASKS.md` (this tracker)
Verification:
- every component/decision cites ARC/REQ/PRN IDs
- scope matches `REQUIREMENTS.md` only (no speculative layers)
Outcome: docs committed as `TASK-001`.

### TASK-002 — Repo skeleton: Compose runtime, backend shell, frontend shell
Status: COMPLETED
Implements: ARC-008, DES-101 (build), NFR-001, PRN-003, PRN-007
Depends on: TASK-001
Work:
- `docker-compose.yml` (db/backend/frontend, named volume, healthchecks, `depends_on: service_healthy`, `demo` profile flag for seeding)
- backend: Maven project (`java 21`, Spring Boot 3, web, jdbc, flyway, snakeyaml), `application.yml`, `V1__init.sql`, minimal health controller, Dockerfile (multi-stage)
- frontend: Vite + React 18 + TS scaffold, `nginx.conf` proxy, Dockerfile, minimal page
- `Makefile`: `demo`, `stop`, `clean-data`, `verify`, `smoke` (latter two to be fleshed out in later tasks)
- `README.md` skeleton (filled in TASK-012)
Verification:
- `make demo` from this tree reaches 3 `healthy` containers
- backend responds on `:8080`, frontend on `:3000`, Flyway applies V1
Outcome:
- `make demo` → "All 3 services healthy." (db/backend/frontend all `healthy`); backend responds on `:8080`, frontend HTML served on `:3010` (port assumption recorded in ARCHITECTURE.md: host `:3000` already occupied)
- Flyway V1 applied: `drafts`, `sops`, `sop_versions` tables present; named volume `sopdemo_sop-pg` created
- local quality gates: `cd backend && mvn -q test` exit 0; `cd frontend && npm run typecheck && npm test && npm run lint && npm run build` all pass (1 test green, placeholder shell)
- note: UI placeholder page replaced in TASK-011

### TASK-003 — Identity, authorization, CORS, error handling
Status: COMPLETED
Implements: DES-206, DES-207, DES-205, FR-001, IR-001, NFR-020, PRN-004
Depends on: TASK-002
Work:
- `IdentityFilter` (401 missing/unknown, author/consumer roles), role check (403)
- CORS confined to configured origin with `X-Demo-User` allowed
- single `@RestControllerAdvice` → `{code,message,issues}`; no internals leak; 500 generic
- integration tests: 401 on missing/unknown identity; 403 consumer on author op; 404 unknown endpoint shape; error body shape
Verification:
- tests green; error body matches IR-001 exactly
Outcome:
- `mvn test` → `Tests run: 9, Failures: 0, Errors: 0` in `IdentityAndErrorApiTest` (missing/unknown identity 401, consumer→author 403, author open 200, generic 500 without internals, 404 normalized body, CORS allow/reject)
- fix found + repaired during verification: CORS preflight `OPTIONS` was blocked by the identity filter (no header) → preflight now excluded from identity check (browser handshake, not a user action); documented below as a design note
- testcontainers needed 1.21.4 for Docker Engine 29 (API ≥ 1.44); recorded in `pom.xml`

### TASK-004 — Safe YAML loader and section parser
Status: TODO
Implements: DES-103, DES-104, FR-020, PRN-004, NFR-020
Depends on: TASK-002
Work:
- `YamlSafe`: safe constructor, no aliases, no custom tags, no dup keys, finite numbers, ≤20 nesting (iterative), StackOverflowError guard
- `SectionParser`: front matter + 7 named sections in spec order; machine sections exactly one ` ```yaml ` block; prose sections ≥1 bullet; reject any other heading/structure
- size gate 65,536 UTF-8 bytes
- unit tests incl. hostile YAML (alias, tag, deep nest, duplicate key, NaN/Inf, non-finite string-as-numerics), bad section ordering, missing section, extra H2, empty bullet list, multi-block machine section
Verification:
- unit suite green; every hostile case returns a controlled `Issue`, never throws past the engine
Outcome: pending.

### TASK-005 — Structural validation (fields, enums, types, unknowns)
Status: TODO
Implements: DES-102, DES-105, FR-021, FR-030 (structural part), spec §1–2
Depends on: TASK-004
Work:
- front matter required keys + enum values; `sop_id` charset
- per-structure required/unknown/type checks (Input, Rule, Condition, Action (+ `max_amount` rules), Boundaries→Escalation, Customer Messages)
- stable `code` set; path built as `section/field` or `section/i/field`; order by (path, code)
- unit tests: missing field, unknown field at every level, wrong enum, non-finite number, bool-as-number, empty text
Verification:
- spec.md valid template parses to `valid=true` with no issues
- each structural rule has a failing test
Outcome: pending.

### TASK-006 — Semantic validation + central financial safety
Status: TODO
Implements: DES-106, DES-105 (semantic), FR-030, FR-032, PRN-005
Depends on: TASK-005
Work:
- unique input/rule/action IDs; reference integrity (condition.input exists, rule.action_ids exist); operator/value-type consistency (booleans `eq` only); `≥1` each
- `FinancialSafety.checkRefundInvariants`: exactly-one-refund, `refund_amount` number exists, limit matches `max_amount`, boundary targets `escalate`; report missing-limit vs missing-escalation independently; `answer_question` forbids refund actions
- unit tests: each semantic rule incl. financial missing-limit / missing-escalation / both; `answer_question` with refund action rejected
Verification:
- `make`-equivalent `mvn -q test` green; spec §3 "removing the refund limit or escalation" produces the two readable issues
Outcome: pending.

### TASK-007 — `POST /validate` API + content preview
Status: TODO
Implements: DES-107, DES-205, IR-001, FR-034, FR-020
Depends on: TASK-006, TASK-003
Work:
- controller wiring `ContentEngine`; 200 for content issues, 400 for malformed envelope, 401/403 identity
- 413 for oversized source (both validate and later draft save)
- integration test: `POST /validate` template → `valid:true`; hostile body → `issues` + `content:null`; 401/403; 413
Verification:
- tests green; `content` is null when invalid; issues ordered (path,code)
Outcome: pending.

### TASK-008 — Draft persistence API
Status: TODO
Implements: DES-202, FR-010, FR-045 (save-clear flag), DR-001, ARC-004
Depends on: TASK-003, TASK-004
Work:
- `DraftStore` (JDBC): `save`, `get`, `list`
- `PUT /drafts/{sop_id}`, `GET /drafts`, `GET /drafts/{sop_id}` — 413/400 envelope checks; revision increments on each save; save clears `publish_failed`
- integration tests (PostgreSQL via Testcontainers): save → rev 1; second save → rev 2; save invalid content is allowed; flag cleared on save
Verification:
- green; restart retains drafts (via compose in TASK-002's stack)
Outcome: pending.

### TASK-009 — Publication service + read model
Status: TODO
Implements: DES-203, DES-204, FR-042, FR-043, FR-045, ARC-005, ARC-006
Depends on: TASK-007, TASK-008
Work:
- `PublicationService` transaction (row lock, upsert pointer, snapshot insert, flag clear) + all 409/422/404 branches
- `GET /sops` (filters+`ORDER BY sop_id`), `GET /sops/{id}`, `GET /sops/{id}/versions/{n}`
- integration tests: invalid publish leaves v1, stale→409, duplicate→409, concurrent (2 threads) → exactly one version increments, v1 immutable, mismatch `sop_id`→422, consumer mutation→403, human/JSON same row
Verification:
- all branches covered; no partial rows after concurrent failures
Outcome: pending.

### TASK-010 — Deterministic seed (demo profile)
Status: TODO
Implements: DES-208, DR-003, ARC-007
Depends on: TASK-008
Work:
- `seed/duplicate-charge.md` = spec.md §3 template
- `SeedInitializer` under `demo` profile, `INSERT … WHERE NOT EXISTS`; idempotent
- integration test: first seed inserts 1 row; second seed does not duplicate or overwrite an edited draft
Verification:
- green; visible in `GET /drafts` after `make demo`
Outcome: pending.

### TASK-011 — React frontend (all screens)
Status: TODO
Implements: DES-301…305, FR-001, FR-010, FR-034, FR-050, FR-052, FR-053, NFR-050, ARC-001
Depends on: TASK-002
Work:
- identity selector (demo-only label) gating all API calls
- `List` (published + author drafts), domain/risk filter controls
- `Editor` (textarea, template insert, save, validate/preview w/ stage-grouped issues, publish w/ saved-revision requirement, unsaved + failed-publish badges, error text)
- `Detail`: `HumanView` + `JsonView` from the same fetched snapshot
- `api.ts` client with `X-Demo-User`; no `dangerouslySetInnerHTML`
- Vitest+RTL tests; `tsc --noEmit`; ESLint; `vite build`
Verification:
- all quality gates green; tests cover identity gating, filter params, two-pane consistency, error text, keyboard-accessible
Outcome: pending.

### TASK-012 — README (startup, journeys, shutdown, data removal, API doc)
Status: TODO
Implements: NFR-001, PRN-007, IR-001 (documented contract)
Depends on: TASK-011
Work:
- URLs, identity selector note, five acceptance journeys mapped to commands
- explicit `make stop` and `make clean-data` (volume removal) semantics
- full API table with request/response shapes (mirrors IR-001)
Verification:
- README self-consistent with the implemented endpoints; no invented behavior
Outcome: pending.

### TASK-013 — `make verify` fully green (recorded)
Status: TODO
Implements: NFR-041, PRN-008
Depends on: TASK-003…011
Work:
- wire `make verify` to backend `mvn -q test`, frontend `tsc --noEmit`+`npm test`+`npm run lint`+`npm run build`
- fix any reds discovered (no weakening of tests)
- record actual commands + results for VERIFICATION.md
Verification:
- `make verify` exit 0 end-to-end; no skipped/`@Disabled` tests
Outcome: pending.

### TASK-014 — `make smoke` against Compose
Status: TODO
Implements: NFR-041, PRN-008, DES-401 (smoke)
Depends on: TASK-009, TASK-010, TASK-011
Work:
- `tests/smoke.sh`: author save→validate→publish; consumer read human+JSON; author bad-rev→409; consumer mutation→403; oversized→413; 401 unknown identity
- exit nonzero on any failure; `make smoke` target
- run against `make demo` stack; record outcome
Verification:
- `make smoke` exit 0; output saved to VERIFICATION.md
Outcome: pending.

### TASK-015 — Acceptance journeys + VERIFICATION.md
Status: TODO
Implements: PRN-008, PRN-009, NFR-041
Depends on: TASK-014
Work:
- Walk each of AC-E2E-001…005 using the stack (manual + scripted evidence)
- Produce `VERIFICATION.md`: coverage table, principle adherence, test/build results, design↔code, known limitations, assumptions
- final `git push` of this commit; confirm
Verification:
- `VERIFICATION.md` committed and pushed; all five journeys have recorded evidence
Outcome: pending.
