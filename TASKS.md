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
Status: COMPLETED
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
Status: COMPLETED
Implements: DES-012, DES-013
Depends on: TASK-001
Work:
- `compose/docker-compose.yml` (3 services: db/backend/frontend, healthchecks, named volume `sop_pgdata`, depends_on service_healthy, ports 5433/8080/8075)
- `backend/Dockerfile` (multi-stage maven:3.9-temurin-21 → eclipse-temurin:21-jre, named `runtime` stage), `frontend/Dockerfile` (node:18-alpine build → nginx:alpine), `nginx.conf` (static UI + `/healthz` + `/api/` proxy to `backend:8080`, so the browser only talks to origin `http://localhost:8075`)
- frontend scaffold: `package.json` (React 18, TS 5, Vite 5, Vitest 2, RTL, ESLint), `tsconfig.json`, `vite.config.ts` (vitest jsdom + setup), `.eslintrc.cjs`, `index.html`, `src/main.tsx`, `src/styles.css`, `src/App.tsx` (tabs: list/drafts/editor/detail; human+JSON views share one snapshot object)
Verification:
- `docker compose -f compose/docker-compose.yml build` → both built images (postgres image pre-cached); backend image boots (verified `docker run --entrypoint sh compose-backend -c 'command -v wget'` → /usr/bin/wget)
- `npx tsc --noEmit` → clean; `npm run build` → ✓ built in 468ms (dist 161.6 kB)
- outcome: delivery committed + pushed

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
Status: COMPLETED
Implements: DES-003, FR-020, NFR-020
Depends on: TASK-001
Work:
- `SafeYaml.load(String, String path)` → `Object` using `SafeConstructor`, alias token-scan, depth walk, finite-number walk
- map failures to issue codes/path
Verification:
- unit tests: scalar alias rejected, list alias rejected, `*` inside a string NOT rejected, 22-level nested list rejected, 20-level accepted, `.inf`/`.nan`/`1e400` rejected, custom tag rejected, duplicate key rejected, `BigInteger` accepted, nested `map>list>map` accepted

### TASK-005 — SectionParser (front-matter + section split)
Status: COMPLETED
Implements: DES-004, FR-020, FR-021
Depends on: TASK-004
Work:
- `SectionParser.split(source)` → `ParsedDocument` (front-matter text, ordered section map, issues)
- prose-section bullets and machine-section single-yaml-block enforcement
Verification:
- unit tests: valid template → 7 sections + front-matter; missing front-matter → issue; duplicate heading → issue; unknown heading → issue; `#` / `###` in body → issue; machine section with 0 or 2 yaml blocks → issue; prose section with non-bullet line → issue

### TASK-006 — Front-matter + type + reference validators
Status: COMPLETED
Implements: DES-001, DES-005 (front-matter/type/reference), FR-030, PRN-005
Depends on: TASK-005
Work:
- `FrontMatterValidator`, `SectionValidator` (shape + unknown keys at every level), `TypeValidator`, `ReferenceValidator`
- deterministic `(path,code)` sort + dedup
Verification:
- unit tests cover: unknown front-matter key, missing front-matter key, bad enum (domain/risk/intent/autonomy), `sop_id` bad format, refund-domain mismatch, unknown field in input/rule/action/escalation, wrong value type, boolean `gt`, missing input ref, missing action ref, duplicate input/rule/action id, no inputs/rules/actions, empty required text

### TASK-007 — Financial safety validator (FR-032)
Status: COMPLETED
Implements: ARC-005, FR-032, DES-005 (financial)
Depends on: TASK-006
Work:
- `FinancialValidator.validate(parsed)` → emits separate `FIN_*` issues
- exactly one refund action; positive finite `max_amount`; `refund_amount` number input; escalation boundary matching action/input/op/amount==max_amount; target escalate action
- `answer_question` must have no refund actions (anti-relabel)
Verification:
- unit tests: valid template → no FIN issues; missing refund action → `FIN_REFUND_MISSING`; missing/zero/negative `max_amount` → `FIN_REFUND_MAX_AMOUNT`; missing `refund_amount` input → `FIN_REFUND_AMOUNT_INPUT`; missing escalation → `FIN_ESCALATION_MISSING`; wrong amount (`!= max_amount`) → `FIN_ESCALATION_BOUND`; answer_question + refund action → semantic violation

### TASK-008 — CanonicalBuilder + pipeline orchestration
Status: COMPLETED
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
Status: COMPLETED
Implements: ARC-007, DES-008a, FR-010
Depends on: TASK-001
Work:
- `Draft` entity/repo, `DraftService.save/get/list` with 413, upsert semantics, revision++ on update, clear `publish_failed_at` on save
- 413 `SOURCE_TOO_LARGE` for >64 KiB
Verification:
- integration tests (PostgreSQL 16 via docker): first save → revision 1, second save → revision 2, `publish_failed_at` cleared on save, 413 `SOURCE_TOO_LARGE` on >64 KiB source, 404 missing draft — all PASS (`DraftApiTest`, 5 tests)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

### TASK-010 — Identity filter + role enforcement
Status: COMPLETED
Implements: ARC-002, DES-007, FR-001
Depends on: TASK-001
Work:
- `IdentityFilter` (401 MISSING_IDENTITY/unknown), `Role.requireAuthor` (403), wired to all 8 endpoints via `WebConfig` (FilterRegistrationBean on `/api/v1/*`)
- 5xx generic envelope via `GlobalExceptionHandler` (no SQL/stack traces, PRN-004)
Verification:
- integration tests: missing header → 401 MISSING_IDENTITY; unknown identity → 401; consumer on validate/save-draft/list-drafts/get-draft/publish/get-version → 403; consumer CAN read list/detail → 200 — all PASS (`IdentityTest`, 4 tests)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

### TASK-011 — Validate endpoint
Status: COMPLETED
Implements: DES-009, FR-034, IR-001
Depends on: TASK-008, TASK-010
Work:
- `AuthorApi.validate` → `{valid, issues, content}`; 200 even with issues; 400 MALFORMED on missing/blank source; hostile YAML yields controlled issues not a crash
Verification:
- integration tests: valid → 200 valid:true + content; invalid (bad domain) → 200 valid:false, content null, non-empty issues; missing-limit + missing-escalation → 200 with BOTH `FIN_REFUND_MAX_AMOUNT` and `FIN_ESCALATION_MISSING` (AC-E2E-002); missing source → 400; blank source → 400; hostile alias (`*dup`) → 200 valid:false + `ALIAS` issue — all PASS (`ValidateApiTest`, 6 tests)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

### TASK-012 — Publish service + endpoint (atomic)
Status: COMPLETED
Implements: ARC-008, DES-008b, FR-042, FR-043, FR-045
Depends on: TASK-009, TASK-011
Work:
- `PublishService.publish`: stale check (409 STALE_REVISION), re-validate saved source (422 VALIDATION_FAILED), advisory lock, next-version insert, `sop_current` upsert, indicator set on failure / cleared on success
- `AuthorApi.publish` → envelope
- Design correction during implementation (design drift, per skill): the FR-045 failure indicator must *persist* across the 422, which a single `@Transactional publish()` cannot guarantee (the indicator write would roll back with the thrown error). Restructured into `publish()` orchestration + `recordPublishFailure()` (own committed transaction) + `commitPublish()` (atomic publication writes). Atomicity of successful publication is unchanged; the DES-008b "single transaction" wording applies to the publication writes, not to the independent indicator record. See VERIFICATION.md (design-to-code consistency).
Verification:
- integration tests: publish v1 (200, `version:1`, envelope content, `sop_current`=1); stale revision → 409 STALE_REVISION; duplicate same-revision → 409 PUBLICATION_CONFLICT (unique constraint); invalid publish → 422 VALIDATION_FAILED + `publish_failed_at` PERSISTED + v1 unchanged + indicator cleared on next save (AC-E2E-004); publishers of different revisions → distinct versions 2 & 3, both commit, both retrievable; concurrent 4 publishers of same revision → exactly 1 success + 3×409, 1 row, current=1; historical v1 after v2 → identical content (FR-043) — all PASS (`PublishAndReadTest`, 10 tests)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

### TASK-013 — Read API (list/detail/versions)
Status: COMPLETED
Implements: ARC-009, ARC-010, DES-009, DES-008c, FR-050, FR-045
Depends on: TASK-012
Work:
- `PublicApi.list` (filters AND-ed; `sop_id` sort; `[]` on no match; 400 `INVALID_FILTER`), `get` (current envelope; 404 if none), `getVersion` (author-only; 404 if absent)
Verification:
- integration tests: no filter → ≥1 current; `domain=Billing&risk=medium` → matches; non-matching filter → 200 `[]`; invalid `domain=Marketing` → 400 `INVALID_FILTER`; detail with no publication → 404; human & JSON (versions/1) identical content/sop_id/version; consumer on historical → 403 — all PASS (`PublishAndReadTest`, `IdentityTest`)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

### TASK-014 — Seed initializer (demo profile)
Status: COMPLETED
Implements: ARC-011, DES-010, DR-003
Depends on: TASK-009
Work:
- `SeedInitializer` `@Profile("demo")` + empty-table guard (`drafts.count() > 0` → skip); `SeedTemplate.BILLING_REFUND` = spec §3 verbatim, `sop_id BILL-REFUND-001`
Verification:
- integration tests: first seed → draft `BILL-REFUND-001` revision 1 with the verbatim spec §3 source; second invocation is a no-op (same row, revision unchanged, no duplicates); after author edit + save (rev 2), a re-run does not overwrite; seed creates no publication (consumer detail 404), and author can then publish the seeded draft (AC-E2E-001) — all PASS (`SeedInitializerTest`, 4 tests)
- outcome: `mvn -f backend/pom.xml test` → Tests run: 82, Failures: 0, Errors: 0

## Phase D — Frontend

### TASK-015 — Frontend API client + identity context
Status: COMPLETED
Implements: DES-012, ARC-001
Depends on: TASK-002
Work:
- `src/api/client.ts` (typed fetch wrapper: `X-Demo-User` + `Content-Type`, query string helper that drops empty/undefined, `ApiError(status, code, message, issues)` on non-2xx), `src/api/types.ts` (Issue / Envelope / Content / DraftSummary / SopSummary), `src/state/IdentityContext.tsx` (React context with `useState`)
- `IdentitySelector` component (select + "Current: …" text, labeled demo-only)
Verification:
- Vitest (client.test.tsx, 6 tests): correct URL `/api/v1/...`; headers `X-Demo-User` + `Content-Type: application/json` for POST; query params (domain present, risk empty → dropped); non-2xx raises ApiError with code/message/`status`/`issues[]`; 401/403 body surfaces; 422 issue list is preserved; IdentityProvider persists identity across re-renders
- outcome: `npx vitest run tests/client.test.tsx` → 6/6; `npx tsc --noEmit` clean; `npm run lint` clean

### TASK-016 — SOP list + filters + drafts list
Status: COMPLETED
Implements: FR-050, FR-001
Depends on: TASK-015
Work:
- `src/components/SopList.tsx` (table + `a.row-link` anchors; empty state "No published SOPs match"), `src/components/FilterBar.tsx` (domain/risk selects with "All" defaults), `src/components/DraftList.tsx` (revision + "publish_failed" indicator; empty state)
- App wires consumer to only the List tab; author additionally gets the Drafts tab + Editor
Verification:
- Vitest (list-filter.test.tsx, 6 tests): SopList renders rows with identity+version; empty-state text for `[]`; rows are `<a href>` anchors with `tabIndex >= 0`; FilterBar dropdowns call `onDomain`/`onRisk`; DraftList shows revision and "Publish failed — …" for flagged drafts; empty-state text for `[]`
- outcome: 6/6 vitest pass; `tsc`+`lint` clean

### TASK-017 — Editor + validate panel + publish flow
Status: COMPLETED
Implements: FR-010, FR-020, FR-034, FR-042, FR-045
Depends on: TASK-015, TASK-016
Work:
- `src/template.ts` — byte-for-byte spec §3 template (matches `Tpl.VALID`/`SeedTemplate.BILLING_REFUND`)
- `src/components/Editor.tsx` — textarea + "Insert valid template" + Save draft + Validate/preview + Publish; `dirty = savedSnapshot != null && source !== savedSnapshot`; Publish disabled when `dirty || revision == null` with visible helper text ("Publish is disabled until the draft is saved."); consumer shows a "author-only" panel and hides every action button
- `src/components/ValidationPanel.tsx` — role="alert"; groups by `stage`; each issue shows `[STRUCTURAL]` / `[SEMANTIC]` label + `code` + `message` + `path`
- Publish flow: `client.publish(sopId, revision, identity)`; on 422 ApiError the Editor captures `e.issues` and surfaces them in the ValidationPanel (FR-045)
Verification:
- Vitest (editor.test.tsx, 6 tests): "Insert valid template" writes the exact `TEMPLATE_SOURCE` (byte equal) and includes `sop_id: BILL-REFUND-001`; after save, `btn-publish` becomes enabled; revision label `revision N` shown; a single keystroke re-triggers the `dirty` indicator; consumer has no `btn-publish` and sees the author-only text; ValidationPanel renders `[STRUCTURAL] C_FRONTMATTER_ENUM` and `[SEMANTIC] FIN_REFUND_MAX_AMOUNT` with separate headings
- outcome: 6/6 vitest pass

### TASK-018 — Human view + JSON view (from same snapshot)
Status: COMPLETED
Implements: FR-052, FR-053
Depends on: TASK-015, TASK-016
Work:
- `src/components/HumanView.tsx` — renders envelope identity + version + owner + domain/risk/intent/max_autonomy chips + policy ul + inputs table + rules table + actions table (with `max_amount`) + boundaries table + customer messages; every user-controlled string is passed as a React text child (auto-escape), and every action/message block carries a visible italic "Describes the SOP — this app does not execute this action or send this message." label (`.msg-label`)
- `src/components/JsonView.tsx` — `<pre className="json">{JSON.stringify(envelope, null, 2)}</pre>` (full envelope)
- `App.tsx` `DetailPane` keeps a single `useState<Envelope|null>` — the object is the ONE snapshot both views render from (FR-053 "both views use the same fetched snapshot")
Verification:
- Vitest (views.test.tsx, 6 tests, jsdom): identity + version + owner + domain + "risk: medium" visible; policy/inputs/rules/actions/boundaries/messages all rendered; the "does not execute" disclaimer appears ≥ 2 times; **XSS**: description `'<img src=x onerror=window.__pwned=true> <script>window.__pwned=1</script> '` → no `<img>` in DOM, the literal text is visible, `globalThis.__pwned` undefined; JsonView's `<pre>` contains `sop_id`, `version: 2`, `published_at`, `content`; human and JSON views agree on `sop_id: BILL-REFUND-001` + `version: 2` from the **same** snapshot object reference
- outcome: 6/6 vitest pass

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
