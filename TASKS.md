---
Artifact: TASKS.md
Stage: 3 (of 6) — maintained continuously through implementation
Status: tracker
---

# SOP Demo — Task Tracker

Statuses: `TODO`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`.
Working branch: `ft-opencode-01` → remote `origin` (github.com/kumarmm0han/harness-benchmark).
Each completed task is committed and pushed before the next starts. Delivery status is tracked separately from implementation status; a completed task with a failed push is reported as *delivery pending*, never *pushed*.

## Phase A — Foundation

### TASK-001 — Scaffold backend buildable skeleton
Status: TODO
Implements: DES-000, DES-011 (config skeleton)
Depends on: (stage 3)
Work:
- `backend/pom.xml` (Spring Boot 3.5.3, web, validation, jdbc, data-jpa, flyway, actuator, test, testcontainers/postgresql; snakeyaml via BOM)
- `SopApplication`, `application.yml`, Flyway `V1__schema.sql` (DES-002), health endpoint
- `global` error handler + exception types (DES-009/011) so the app boots and reports the right envelope
Verification:
- `mvn -q -f backend/pom.xml -DskipTests package` succeeds and produces a jar
- app boots against a throwaway container exposing `/actuator/health` = UP

### TASK-002 — Scaffold compose + frontend buildable skeleton
Status: TODO
Implements: DES-012, DES-013
Depends on: TASK-001
Work:
- `compose/docker-compose.yml` (3 services, healthchecks, named volume, local defaults)
- `backend/Dockerfile` (multi-stage maven→jre), `frontend/Dockerfile` (node build→nginx), `nginx.conf` + `/healthz`
- frontend Vite scaffold, `main.tsx`, minimal `App.tsx`, `package.json`, lint + tsconfig + vitest config
Verification:
- `docker compose build` succeeds for all three services
- `npx tsc --noEmit` and `npm run build` succeed in frontend

### TASK-003 — Root Makefile + scripts + CI-independent entry points
Status: TODO
Implements: DES-014, NFR-041
Depends on: TASK-002
Work:
- `Makefile` targets: `verify`, `demo`, `smoke`, `down`, `down-clean`, `backend-test`, `frontend-test`
- `scripts/smoke.sh` (primary journey against Compose; nonzero on failure)
- `scripts/smoke` wait-for-health helper (poll `/actuator/health`, not fixed sleeps)
Verification:
- `make demo` brings up three healthy services (healthchecks green)
- `make smoke` runs end-to-end and exits 0 against a seeded stack (run only after TASK-014)
- `make down-clean` removes containers and the named volume

## Phase B — Backend core (deterministic pipeline)

### TASK-004 — SafeYAML loader (alias/depth/finite/tag/finite)
Status: TODO
Implements: DES-003, FR-020, NFR-020
Depends on: TASK-001
Work:
- `SafeYaml.load(String, String path)` → `Object` using `SafeConstructor`, alias token-scan, depth walk, finite-number walk
- map failures to issue codes/path
Verification:
- unit tests: scalar alias rejected, list alias rejected, `*` inside a string NOT rejected, 22-level nested list rejected, 20-level accepted, `.inf`/`.nan`/`1e400` rejected, custom tag rejected, duplicate key rejected, `BigInteger` accepted, nested `map>list>map` accepted

### TASK-005 — SectionParser (front-matter + section split)
Status: TODO
Implements: DES-004, FR-020, FR-021
Depends on: TASK-004
Work:
- `SectionParser.split(source)` → `ParsedDocument` (front-matter text, ordered section map, issues)
- prose-section bullets and machine-section single-yaml-block enforcement
Verification:
- unit tests: valid template → 7 sections + front-matter; missing front-matter → issue; duplicate heading → issue; unknown heading → issue; `#` / `###` in body → issue; machine section with 0 or 2 yaml blocks → issue; prose section with non-bullet line → issue

### TASK-006 — Front-matter + type + reference validators
Status: TODO
Implements: DES-001, DES-005 (front-matter/type/reference), FR-030, PRN-005
Depends on: TASK-005
Work:
- `FrontMatterValidator`, `SectionValidator` (shape + unknown keys at every level), `TypeValidator`, `ReferenceValidator`
- deterministic `(path,code)` sort + dedup
Verification:
- unit tests cover: unknown front-matter key, missing front-matter key, bad enum (domain/risk/intent/autonomy), `sop_id` bad format, refund-domain mismatch, unknown field in input/rule/action/escalation, wrong value type, boolean `gt`, missing input ref, missing action ref, duplicate input/rule/action id, no inputs/rules/actions, empty required text

### TASK-007 — Financial safety validator (FR-032)
Status: TODO
Implements: ARC-005, FR-032, DES-005 (financial)
Depends on: TASK-006
Work:
- `FinancialValidator.validate(parsed)` → emits separate `FIN_*` issues
- exactly one refund action; positive finite `max_amount`; `refund_amount` number input; escalation boundary matching action/input/op/amount==max_amount; target escalate action
- `answer_question` must have no refund actions (anti-relabel)
Verification:
- unit tests: valid template → no FIN issues; missing refund action → `FIN_REFUND_MISSING`; missing/zero/negative `max_amount` → `FIN_REFUND_MAX_AMOUNT`; missing `refund_amount` input → `FIN_REFUND_AMOUNT_INPUT`; missing escalation → `FIN_ESCALATION_MISSING`; wrong amount (`!= max_amount`) → `FIN_ESCALATION_BOUND`; answer_question + refund action → semantic violation

### TASK-008 — CanonicalBuilder + pipeline orchestration
Status: TODO
Implements: ARC-006, DES-005, DES-006, FR-021
Depends on: TASK-004..007
Work:
- `CanonicalBuilder.build(parsed)` → `Content`
- `ValidatorPipeline.validate(source)` → `PipelineResult {valid, issues, content}`
- `ValidationService` thin wrapper
Verification:
- unit tests: identical source → structurally identical content (assert on canonical fields and issue ordering); invalid → `content==null`; valid template → exact canonical fields per spec §4; bullet de-marking + trimming; array order preserved

## Phase C — Backend persistence and APIs

### TASK-009 — Draft service + persistence + repository
Status: TODO
Implements: ARC-007, DES-008a, FR-010
Depends on: TASK-001
Work:
- `Draft` entity/repo, `DraftService.save/get/list` with 413, upsert semantics, revision++ on update, clear `publish_failed_at` on save
- 413 `SOURCE_TOO_LARGE` for >64 KiB
Verification:
- integration tests (Testcontainers PG): first save → revision 1, second save → revision 2, update does not create new row, `publish_failed_at` cleared on save, 413 on 65537-byte source, 404 missing draft

### TASK-010 — Identity filter + role enforcement
Status: TODO
Implements: ARC-002, DES-007, FR-001
Depends on: TASK-001
Work:
- `IdentityFilter` (401), `Role.require` helper (403), wiring to all 8 endpoints
Verification:
- integration tests: missing header → 401; unknown header value → 401; consumer on author-only op (validate/save/get draft/publish/get version) → 403; author on consumer-allowed op → 2xx

### TASK-011 — Validate endpoint
Status: TODO
Implements: DES-009, FR-034, IR-001
Depends on: TASK-008, TASK-010
Work:
- `AuthorApi.validate` → `{valid, issues, content}`; 200 even with issues; 400 on malformed envelope
Verification:
- integration tests: valid → 200 + content; invalid (missing refund limit) → 200 + issues (both structural and semantic present), content null; consumer → 403; missing source → 400; hostile alias source → 200 + PARSE_FAILED issue

### TASK-012 — Publish service + endpoint (atomic)
Status: TODO
Implements: ARC-008, DES-008b, FR-042, FR-043, FR-045
Depends on: TASK-009, TASK-011
Work:
- `PublishService.publish`: stale check (409), re-validate saved source (422), advisory lock, next-version insert, `sop_current` upsert, indicator set on failure / cleared on success
- `AuthorApi.publish` → envelope
Verification:
- integration tests: publish v1 (returns envelope, `sop_current` set); stale revision → 409; duplicate same-revision → 409; invalid publish → 422 + `publish_failed_at` set + v1 unchanged + indicator cleared on next save; concurrent two publishers of different revisions → distinct versions (2 & 3) and both commit; snapshot content immutable after next save

### TASK-013 — Read API (list/detail/versions)
Status: TODO
Implements: ARC-009, ARC-010, DES-009, DES-008c, FR-050, FR-045
Depends on: TASK-012
Work:
- `PublicApi.list` (filters; `sop_id` sort; `[]` on no match; 400 invalid filter), `get` (current envelope; 404 if none), `getHistorical` (author-only; 404 if absent)
Verification:
- integration tests: no filter → all current; `domain=Billing` → only Billing; both filters AND; invalid domain → 400; empty → 200 `[]`; detail with no publication → 404; historical v1 after v2 published → returns v1 exactly; consumer on historical → 403

### TASK-014 — Seed initializer (demo profile)
Status: TODO
Implements: ARC-011, DES-010, DR-003
Depends on: TASK-009
Work:
- `SeedInitializer` `@Profile("demo")` + empty-table guard; `SeedTemplate` constant = spec §3 verbatim
Verification:
- integration tests: first start → draft row `BILL-REFUND-001` revision 1; second invocation is a no-op (row unchanged); after author edits the draft and it is saved, a re-run does not overwrite; no publication is created by seed (consumer detail 404)

## Phase D — Frontend

### TASK-015 — Frontend API client + identity context
Status: TODO
Implements: DES-012, ARC-001
Depends on: TASK-002
Work:
- `src/api/client.ts` (fetch wrapper, X-Demo-User, typed errors), `src/api/types.ts`, `src/state/IdentityContext.tsx`
- `IdentitySelector` component
Verification:
- Vitest: identity change persists to every subsequent fetch (mock fetch); 401/403 body surfaces as typed error; correct header is sent

### TASK-016 — SOP list + filters + drafts list
Status: TODO
Implements: FR-050, FR-001
Depends on: TASK-015
Work:
- `SopList`, `FilterBar`, `DraftList`; empty state; author-only draft list
Verification:
- Vitest: renders published rows with identity+version; filters (domain/risk) appear in query string; empty state text when `[]`; draft list visible to author and hidden to consumer; keyboard: tab-focusable rows

### TASK-017 — Editor + validate panel + publish flow
Status: TODO
Implements: FR-010, FR-020, FR-034, FR-042, FR-045
Depends on: TASK-015
Work:
- `Editor` (textarea, unsaved guard, template insert, save/validate/publish), `ValidationPanel` (stage-grouped, `[STRUCTURAL]`/`[SEMANTIC]` labels, not color-only)
- publish uses saved revision; unsaved edits block publish
Verification:
- Vitest: template insertion inserts the exact spec §3 text; unsaved guard disables publish; save success refreshes revision display; validation panel shows structural vs semantic distinctly; publish failure (422) surfaces issues + "publish_failed" indicator on the draft row; consumer cannot see the publish button

### TASK-018 — Human view + JSON view (from same snapshot)
Status: TODO
Implements: FR-052, FR-053
Depends on: TASK-015
Work:
- `HumanView` (strings rendered as React text children → auto-escaped), "describes an SOP" labels on every action/message block
- `JsonView` (`<pre>{ JSON.stringify(envelope, null, 2) }</pre>`)
- shared snapshot state; human and JSON views render from the same fetched object
Verification:
- Vitest + DOM: action description containing `<img onerror=...>` is rendered as literal text (no `img` element created); JSON view contains `sop_id`, `version`, `published_at`, `content`; human and JSON views show the same `sop_id`+`version` for the same snapshot

## Phase E — Integration + delivery

### TASK-019 — Smoke script + make targets (NFR-041)
Status: TODO
Implements: DES-014, NFR-041
Depends on: TASK-014, TASK-018
Work:
- `scripts/smoke.sh`: primary journey (save→validate→publish→list→detail→consumer 403→historical) against the Compose stack
- `Makefile` final wiring (demo, verify, smoke, down, down-clean)
Verification:
- `make demo` then `make smoke` exits 0; `make demo` again (restart) → seed preserved, draft still present, publication still present (AC-E2E-005)

### TASK-020 — Documentation (README + API.md)
Status: TODO
Implements: NFR-001, PRN-007
Depends on: TASK-019
Work:
- `README.md`: URLs, identity selection, the five acceptance journeys, shutdown, data deletion, local-only disclaimer
- `API.md`: normative request/response shapes for all 8 endpoints
Verification:
- each of the five journeys maps to a documented sequence; every `IR-001` endpoint has a documented request/response; README has explicit "delete data" command (`make down-clean`)

### TASK-021 — Full `make verify` + fix drift
Status: TODO
Implements: NFR-041, PRN-008
Depends on: TASK-001..020
Work:
- run `make verify` (backend build+unit, PG integration, frontend tests, tsc, lint, prod build)
- fix failures with smallest justified corrections (no assertion removal, no requirement weakening)
Verification:
- `make verify` exits 0 on a clean checkout; if any test was fixed, the diff and reason are recorded in VERIFICATION.md; if a lower-level artifact (design/task) needed a correction, the correction is in that file with a reference

### TASK-022 — Final verification + delivery
Status: TODO
Implements: PRN-008, PRN-009
Depends on: TASK-001..021
Work:
- `VERIFICATION.md`: requirement coverage table, principle adherence, test/build results (actual commands + outcomes), design-to-code consistency, known limitations, assumptions
- confirm each completed task is committed + pushed (delivery status)
- report honestly: any task with a failed push is delivery pending; unresolved limitations listed
Verification:
- `VERIFICATION.md` exists and references every `REQ*` and `PRN*` ID with a status; the final commit is on `ft-opencode-01` and pushed (delivery confirmed by `git ls-remote`); a summary of the five acceptance journeys is present in the doc
