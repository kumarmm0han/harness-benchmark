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
Status: COMPLETED
Implements: DES-103, DES-104, FR-020, PRN-004, NFR-020
Depends on: TASK-002
Work:
- `YamlSafe`: safe constructor, no aliases, no custom tags, no dup keys, finite numbers, ≤20 nesting (iterative), StackOverflowError guard
- `SectionParser`: front matter + 7 named sections in spec order; machine sections exactly one ` ```yaml ` block; prose sections ≥1 bullet; reject any other heading/structure
- size gate 65,536 UTF-8 bytes
- unit tests incl. hostile YAML (alias, tag, deep nest, duplicate key, NaN/Inf, non-finite string-as-numerics), bad section ordering, missing section, extra H2, empty bullet list, multi-block machine section
Verification:
- unit suite green; every hostile case returns a controlled `Issue`, never throws past the engine
Outcome:
- new: `content/{Issue,Codes,SourceLimits,SourceTooLargeException,YamlSafe,ParsedDoc,SectionParser}.java` + tests `content/{YamlSafeTest,SectionParserTest}.java` + `Fixtures` (spec §3-compliant refund + answer documents)
- `mvn -q test` → `Tests run: 49, Failures: 0, Errors: 0` (YamlSafeTest 8/8, SectionParserTest 9/9; alias/custom-tag/duplicate-key/deep-nest/NaN/Inf all return controlled issues; depth-20 accepted, depth-21 rejected without StackOverflowError)
- design note: "reject aliases" implemented as an event pass that rejects *any* alias reference (scalar or collection), stronger than SnakeYAML's built-in non-scalar counter

### TASK-005 — Structural validation (fields, enums, types, unknowns)
Status: COMPLETED
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
Outcome:
- `content/StructuralValidator.java` enforces the full spec §2 table (required fields, unknown-field rejection at every object level, enums, value types, `max_amount` only on refund, `sop_id`/`name`/`id` charsets) with paths `frontmatter.*`, `inputs[i].*`, `rules[i].conditions[c].*`, `actions[i].*`, `boundaries.escalation[i].*`, `customer_messages.*`
- issue codes centralized in `content/Codes.java` (PRN-005); sorting by (path, code) in the engine
- covered by `ContentValidationTest` structural cases (missing/unknown front-matter field, invalid enum, malformed sop_id, non-string value, unknown input field, max_amount misuse) — all green within `Tests run: 49, Failures: 0`

### TASK-006 — Semantic validation + central financial safety
Status: COMPLETED
Implements: DES-106, DES-105 (semantic), FR-030, FR-032, PRN-005
Depends on: TASK-005
Work:
- unique input/rule/action IDs; reference integrity (condition.input exists, rule.action_ids exist); operator/value-type consistency (booleans `eq` only); `≥1` each
- `FinancialSafety.checkRefundInvariants`: exactly-one-refund, `refund_amount` number exists, limit matches `max_amount`, boundary targets `escalate`; report missing-limit vs missing-escalation independently; `answer_question` forbids refund actions
- unit tests: each semantic rule incl. financial missing-limit / missing-escalation / both; `answer_question` with refund action rejected
Verification:
- `make`-equivalent `mvn -q test` green; spec §3 "removing the refund limit or escalation" produces the two readable issues
Outcome:
- `content/{SemanticValidator,FinancialSafety,Compiler,ContentEngine}.java` — semantic stage runs only after structural pass; all discovered issues returned (FR-034), sorted by (path, code)
- financial invariants centralized in `FinancialSafety` keyed by intent: `REFUND_LIMIT_MISSING` and `REFUND_ESCALATION_MISSING` emitted independently (both fire when both are absent); `answer_question` + refund action → `REFUND_NOT_ALLOWED`; `refund_duplicate_charge` on non-Billing domain → `REFUND_INTENT_REQUIRES_BILLING`
- `ContentValidationTest` covers: duplicate ids, bad references (condition input, action_ids, boundary target), boolean+`gt` mismatch, empty input list, boundary not-targeting-escalate, boundary amount ≠ max_amount, both financial errors at once, deterministic ordering — `mvn -q test` → `Tests run: 49, Failures: 0, Errors: 0`

### TASK-007 — `POST /api/v1/validate` API + content preview
Status: COMPLETED
Implements: DES-107, DES-205, IR-001, FR-034, FR-020
Depends on: TASK-006, TASK-003
Work:
- controller wiring `ContentEngine`; 200 for content issues, 400 for malformed envelope, 401/403 identity
- 413 for oversized source (shared mapping, also used by draft saves)
- integration test: `POST /validate` template → `valid:true`; hostile body → `issues` + `content:null`; 401/403; 413
Verification:
- tests green; `content` is null when invalid; issues ordered (path,code)
Outcome:
- `api/ValidateController`: `POST /api/v1/validate` (author-only) → 200 `{valid, issues, content}`; content null when invalid (IR-001, FR-034); read-only, no state change (verified against `drafts`)
- `api/InvalidRequestException` → 400 `invalid-request`; non-JSON/array bodies → 400; unknown path segments → 400; `SourceTooLargeException` → 413 `source-too-large` (shared with TASK-008 draft saves)
- `ValidateApiTest` (7): valid template → canonical content (sop_id BILL-001); spec §3 mutation → both `refund-limit-missing` + `refund-escalation-missing`, readable messages (no parser internals); deterministic (path, code) ordering; 401/403; malformed envelopes → 400; 65,537-byte source → 413; stateless check via `SELECT count(*) FROM drafts`
- `Fixtures` made public for reuse by draft/publication tests
- `mvn test` → `Tests run: 56, Failures: 0, Errors: 0`

### TASK-008 — Draft persistence API
Status: COMPLETED
Implements: DES-202, FR-010, FR-045 (save-clear flag), DR-001, ARC-004
Depends on: TASK-003, TASK-004
Work:
- `DraftStore` (JDBC): `save`, `get`, `list`
- `PUT /drafts/{sop_id}`, `GET /drafts`, `GET /drafts/{sop_id}` — 413/400 envelope checks; revision increments on each save; save clears `publish_failed`
- integration tests (PostgreSQL via Testcontainers): save → rev 1; second save → rev 2; save invalid content is allowed; flag cleared on save
Verification:
- green; restart retains drafts (via compose in TASK-002's stack)
Outcome:
- `drafts/DraftStore`: single `INSERT … ON CONFLICT DO UPDATE` upsert; revision increments server-side (first save → 1); every save clears `publish_failed` (FR-045) and bumps `saved_at`; `markPublishFailed` for the publication service (TASK-009)
- `drafts/DraftController`: `PUT /api/v1/drafts/{sop_id}` → 200 `{sop_id, revision, source}`; `GET /api/v1/drafts` → author-only `{drafts:[…]}` (FR-050); `GET /api/v1/drafts/{sop_id}` → saved source verbatim (FR-010) or 404 `draft-not-found`
- validation: sop_id charset `[A-Z][A-Z0-9-]{0,63}` else 400; size gate → 413 (FR-020); author-only (401/403); saving allowed before content is valid (IR-001)
- `config/JacksonConfig`: global `SNAKE_CASE` property naming so all IR-001 response fields are `sop_id`/`publish_failed`/`saved_at` (PRN-005 central convention)
- test infrastructure fix: integration tests now share one JVM-scoped PostgreSQL (static start, `@DynamicPropertySource`) so the Spring context cache never points at a container the Testcontainers extension already stopped — `DraftApiTest` (10 cases) + full suite `mvn test` → `Tests run: 66, Failures: 0, Errors: 0`

### TASK-009 — Publication service + read model
Status: COMPLETED
Implements: DES-203, DES-204, FR-042, FR-043, FR-045, ARC-005, ARC-006
Depends on: TASK-007, TASK-008
Work:
- `PublicationService` transaction (row lock, upsert pointer, snapshot insert, flag clear) + all 409/422/404 branches
- `GET /sops` (filters+`ORDER BY sop_id`), `GET /sops/{id}`, `GET /sops/{id}/versions/{n}`
- integration tests: invalid publish leaves v1, stale→409, duplicate→409, concurrent (2 threads) → exactly one version increments, v1 immutable, mismatch `sop_id`→422, consumer mutation→403, human/JSON same row
Verification:
- all branches covered; no partial rows after concurrent failures
Outcome:
- `publish/PublicationService` + `publish/PublicationTx`: validate → check → single-transaction write phase
  (`SELECT … FOR UPDATE` row lock serializes concurrent publishes; re-check under lock; next-version insert;
  pointer upsert; flag clear). `UNIQUE(sop_id, source_revision)` and PK `(sop_id, version)` are the
  final guard — violation 23505 → 409 `duplicate-publication` (FR-042, ARC-006, PRN-006)
- failure branches: no draft → 404 `draft-not-found`; stale revision → 409 `stale-revision`;
  invalid content → 422 `publication-rejected` with sorted issues + `publish_failed` set (previous
  version retained, FR-045); sop_id mismatch → 422 `sop-id-mismatch`
- `POST /api/v1/sops/{sop_id}/publish` (author) → 200 `{sop_id, version, content}` where content is
  the stored canonical snapshot (FR-042)
- `sop/SopController`: list with fixed `domain`∈{Billing,Support} / `risk`∈{low,medium} filters
  (invalid → 400), `ORDER BY sop_id` (FR-050); current snapshot identical for author/consumer
  (FR-053); unpublished → 404 `sop-not-found`, never draft data (FR-045);
  `GET …/versions/{version}` author-only (FR-043), 404 `version-not-found`
- `PublicationApiTest` (9) incl. 2-thread concurrent publish (exactly one 200 + one 409, exactly one
  version row) and immutable v1 after v2; `ReadApiTest` (5) incl. filter semantics and role separation
- `mvn test` → `Tests run: 80, Failures: 0, Errors: 0` (two consecutive stable runs)

### TASK-010 — Deterministic seed (demo profile)
Status: COMPLETED
Implements: DES-208, DR-003, ARC-007
Depends on: TASK-008
Work:
- `seed/duplicate-charge.md` = spec.md §3 template
- `SeedInitializer` under `demo` profile, `INSERT … WHERE NOT EXISTS`; idempotent
- integration test: first seed inserts 1 row; second seed does not duplicate or overwrite an edited draft
Verification:
- green; visible in `GET /drafts` after `make demo`
Outcome:
- `backend/src/main/resources/seed/duplicate-charge.md` = the exact spec.md §3 valid
  template (sop_id `BILL-REFUND-001`)
- `config/SeedInitializer` (`@Profile("demo")`, `ApplicationRunner` after Flyway): atomic
  `INSERT … SELECT … WHERE NOT EXISTS` — fills a missing draft only; repeated startups
  neither duplicate drafts nor reset author edits (DR-003); seed is draft-only, never
  auto-published (the journey's author performs publication)
- publish envelope aligned with spec.md §4: 200 body `{sop_id, version, published_at,
  content}`; `published_at` is the server-assigned UTC timestamp; envelope `sop_id`
  equals `content.sop_id`
- `SeedApiTest` (2, demo profile): seeded draft revision 1 with the exact template source
  and nothing published; author edit → two more boots → single row, revision 2, edited
  source preserved (no clobber, no duplicate); delete + boot restores the seed
- `mvn test` → `Tests run: 82, Failures: 0, Errors: 0` (stable across runs)

### TASK-011 — React frontend (all screens)
Status: COMPLETED
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
Outcome:
- `types.ts` + `api.ts`: typed client, every request carries `X-Demo-User` (FR-001);
  `ApiError` keeps `{code,message,issues}` for text-only error surfacing
- Identity selector labeled "local only — not real authentication"; role-aware nav:
  authors get My drafts/New draft, consumers never see draft surfaces; leaving
  author-only views when switching to consumer
- `Browse`: domain/risk selects (fixed values), AND semantics + `ORDER BY sop_id`
  come from the API; 400 filter errors shown as server text; useful empty state
- `Drafts`: revision + publication-failure indicator as text (NFR-050)
- `Editor`: textarea keeps content through any failure; unsaved-changes badge;
  template insert (spec.md §3), save, validate (stage-grouped issues w/ code,
  message, path; canonical preview only when valid), publish — saves first when
  dirty, publishes the saved revision; 409/422/404/413 surfaced as readable text
  (422 shows the server issue list) (FR-010, FR-034, FR-045, AC-E2E-001/002/004)
- `SopDetail`: ONE fetch supplies identity+version+content; Human and JSON tabs
  render that same snapshot (FR-052/FR-053); all authored strings rendered as
  text (XSS `<script>` sample stays inert, asserted); policy note explains content
  is SOP policy, not executed actions; author-only version picker loads immutable
  historical snapshots (FR-043); 404 explains drafts are never substituted
- Tests: `App.test.tsx` (13) covering all of the above with a fetch router mock
- Gates: `tsc --noEmit` ✓, `eslint` ✓, `vitest run` 13/13 ✓, `vite build` ✓
- Live stack check via UI origin :3010: UI HTML+JS 200, /api proxied, seed draft
  visible, full author→consumer journey (save→publish v1→list/detail/history) OK,
  duplicate publish 409 `duplicate-publication`, consumer mutations 403, no-identity
  401, CORS allows :3010 and rejects other origins

### TASK-012 — README (startup, journeys, shutdown, data removal, API doc)
Status: COMPLETED
Implements: NFR-001, PRN-007, IR-001 (documented contract)
Depends on: TASK-011
Work:
- URLs, identity selector note, five acceptance journeys mapped to commands
- explicit `make stop` and `make clean-data` (volume removal) semantics
- full API table with request/response shapes (mirrors IR-001)
Verification:
- README self-consistent with the implemented endpoints; no invented behavior
Outcome:
- README.md written: URLs table (UI :3010, API /api/v1 via :8080 or the
  UI proxy, Postgres :5432 w/ documented local-only credential);
  identity selector + fixed demo identities (DR-003) + the X-Demo-User header
  contract and 401/403 semantics (FR-001)
- Quickstart `make demo` and the required tooling for `make verify` (Docker,
  JDK 21, Maven, Node 22; Testcontainers for backend integration tests)
- The five acceptance journeys (AC-E2E-001..005) written as concrete UI steps
  with the exact issue codes to expect (refund-limit-missing,
  refund-escalation-missing), including the restart/data-retention journey
- Shutdown (`make stop`, retained volume) and explicit data removal
  (`make clean-data`)
- `make verify` / `make smoke` descriptions
- Full API reference: per-endpoint access level, request, success envelope,
  status-code conventions (400/401/403/404/409/413/422), uniform error shape
  `{code,message,issues}` (IR-001), issue-object shape + deterministic ordering,
  and the real, stable issue-code list (verified against `content/Codes.java`
  and the publication conflict codes) — no invented behavior
- Security/safety notes: labeled local-only identity, content-engine hard
  limits, no execution of authored rules/actions, Postgres integrity +
  atomic publication (row lock + UNIQUE(sop_id,version) /
  UNIQUE(sop_id,source_revision)), CORS limited to the local UI origin
- Repository map tying the layout to the frozen inputs (PRINCIPLES/REQUIREMENTS
  /spec) and the implementation record (ARCHITECTURE/TECHNICAL_DESIGN/TASKS)

### TASK-013 — `make verify` fully green (recorded)
Status: COMPLETED
Implements: NFR-041, PRN-008
Depends on: TASK-003…011
Work:
- wire `make verify` to backend `mvn -q test`, frontend `tsc --noEmit`+`npm test`+`npm run lint`+`npm run build`
- fix any reds discovered (no weakening of tests)
- record actual commands + results for VERIFICATION.md
Verification:
- `make verify` exit 0 end-to-end; no skipped/`@Disabled` tests
Outcome:
- `make verify` exit 0 end-to-end (recorded run, commit `85d1bd0`):
  - backend `cd backend && mvn -q test` — BUILD SUCCESS (82 tests: content
    engine 40, validate API 7, drafts 10, identity/error 9, publication 9,
    read 5, seed 2; Testcontainers PostgreSQL 16); a deliberate 500-path test
    logs the expected `Unexpected error` ERROR line — no real failure
  - frontend `npm ci && npm run typecheck && npm test && npm run lint &&
    npm run build` — tsc clean, Vitest 13/13, ESLint clean (0 warnings),
    Vite production build 38 modules, 164 kB JS (51.96 kB gzip)
- No tests skipped or `@Disabled`; no test weakened or deleted to pass
Outcome: pending.

### TASK-014 — `make smoke` against Compose
Status: COMPLETED
Implements: NFR-041, PRN-008, DES-401 (smoke)
Depends on: TASK-009, TASK-010, TASK-011
Work:
- `tests/smoke.sh`: author save→validate→publish; consumer read human+JSON; author bad-rev→409; consumer mutation→403; oversized→413; 401 unknown identity
- exit nonzero on any failure; `make smoke` target
- run against `make demo` stack; record outcome
Verification:
- `make smoke` exit 0; output saved to VERIFICATION.md
Outcome:
- `tests/smoke.sh` (wired to the existing `make smoke` target) exercises the
  primary journey over HTTP against the running Compose stack, through the UI
  origin proxy by default (`BASE` overridable to hit :8080 directly):
  1. UI origin reachable (HTTP 200)
  2. author `POST /validate` → `valid: true`, 0 issues
  3. author `PUT /drafts/SMOKE-001` → saved revision reported
  4. author `POST /sops/SMOKE-001/publish {revision}` → 200; envelope
     `sop_id` equals `content.sop_id`
  5. consumer `GET /sops?domain=Billing&risk=medium` → contains the exact
     sop_id + version just published (filter AND semantics)
  6. consumer `GET /sops/SMOKE-001` → same version, readable title
  7. author `GET .../versions/{v}` → 200 same snapshot; consumer → 403
  8. no `X-Demo-User` → 401; consumer `PUT /drafts/…` → 403
- Idempotent: re-run saves a fresh revision and publishes the next version;
  verified green on consecutive runs (versions 1→4, 5)
- 409 / 413 paths are covered by backend integration tests
  (`PublicationApiTest`: stale 409 + duplicate 409; `ContentValidationTest`
  / `DraftApiTest`: 413 oversized) since smoke asserts the happy primary path
- Recording found unsupported HTTP methods fell through to the generic 500;
  added a `405 method-not-allowed` mapping in `ApiExceptionHandler` with a
  regression test (`unsupportedMethodIs405`) verified over HTTP; backend suite
  re-run green (83 tests), README status conventions updated with 405

### TASK-015 — Acceptance journeys + VERIFICATION.md
Status: COMPLETED
Implements: PRN-008, PRN-009, NFR-041
Depends on: TASK-014
Work:
- Walk each of AC-E2E-001…005 using the stack (manual + scripted evidence)
- Produce `VERIFICATION.md`: coverage table, principle adherence, test/build results, design↔code, known limitations, assumptions
- final `git push` of this commit; confirm
Verification:
- `VERIFICATION.md` committed and pushed; all five journeys have recorded evidence
Outcome:
- All five acceptance journeys executed live against a clean Compose stack
  (`make clean-data` + `make demo`) through the UI origin — final full run
  **18/18 pass** (AC-002 shows exactly the two semantic issues
  `refund-limit-missing` + `refund-escalation-missing` and a 422 that keeps
  v1 current; AC-004 proves v1 is byte-identical after v2 via matching
  canonical-content sha256 `07373b54…130afe67`).
- `VERIFICATION.md` written: reproduction steps,
  environment, `make verify` (backend 83 tests / 0 fail / 0 skip; frontend
  13/13 + typecheck + lint + build) and `make smoke` (exit 0, recorded output),
  the 18 acceptance assertions with the evidence each relied on, a full
  requirement→evidence traceability table (AC/FR/NFR/IR/DR/PRN), a PRINCIPLES.md
  conformance section (YAGNI/no-placeholder/completeness/testing-first/no
  weakening), and how-to-reproduce.
- Final `git push` confirmed to `origin/pi-01`.
- Known limitations (by design, per REQUIREMENTS/DR-003): demo identities are
  a labeled local mechanism and not real authentication; Postgres ships a
  documented local-only default credential; demo is for local use only.
- Assumption: the five journeys + the safety/refund/escalation rules in the
  template are the authoritative scope for this demo (frozen by spec.md).
- No requirement was weakened, skipped, or replaced with a workaround.
- This is the final task; the deliverable set is
  `ARCHITECTURE.md`, `TECHNICAL_DESIGN.md`, `TASKS.md` (all 15 COMPLETED),
  `README.md`, `VERIFICATION.md`, and the implementation + tests.
