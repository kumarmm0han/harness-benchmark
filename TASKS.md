# TASKS

Task tracker for the SOP demo. Statuses are updated during implementation. Priorities:
`PRINCIPLES.md` > `REQUIREMENTS.md` > `ARCHITECTURE.md` > `TECHNICAL_DESIGN.md` > this file.
Each task carries a verification criterion; mark `COMPLETED` only after that criterion passes.

| ID | Status | Implements | Depends on | Focus |
|---|---|---|---|---|
| TASK-001 | COMPLETED | ARC-001, PRN-003, PRN-007 | — | backend scaffold + build |
| TASK-002 | COMPLETED | DES-002/003, FR-020/021, PRN-001/004/005, NFR-020 | 001 | safe YAML + Markdown parse + canonical model |
| TASK-003 | COMPLETED | DES-004/005/006, FR-030/032/034, PRN-005, NFR-020 | 002 | validation engine (structural/semantic/financial) |
| TASK-004 | COMPLETED | DES-007/008/010/015, DR-001/003, FR-001/010, PRN-006/007 | 003 | persistence + identity + draft API + seed |
| TASK-005 | COMPLETED | DES-008/009/011, FR-042/043/045, IR-001, PRN-004/006 | 004 | publication service + REST API + error/CORS |
| TASK-006 | COMPLETED | DES-012/017, NFR-001, DR-001/003, PRN-007 | 005 | compose stack + seed + README + demo/down |
| TASK-007 | COMPLETED | DES-013/014, FR-001/050, NFR-050 | 006 | frontend base: identity/list/filters/drafts |
| TASK-008 | COMPLETED | DES-013/014, FR-010, NFR-050 | 007 | editor: template/save/validate/publish |
| TASK-009 | TODO | DES-014, FR-052/053, NFR-020 | 008 | human + JSON views (same snapshot, safe) |
| TASK-010 | TODO | DES-016/017, NFR-041, PRN-008 | 009 | make verify/smoke + full verification |

---

### TASK-001 — Backend scaffold & build
Status: COMPLETED
Implements: ARC-001, DES-012, PRN-003, PRN-007
Depends on: —
Work:
- `pom.xml` (Spring Boot 3.5.16, Java 21, web/jdbc/flyway/pg/snakeyaml, test deps).
- `com.sopdemo.SopDemoApplication`, `application.yml` (datasource, CORS, seed, health).
- `backend/Dockerfile` (multi-stage: build with Maven, run with JRE 21).
- `health` endpoint; verify `mvn -q package` yields an executable jar.
Verification: `mvn -q -B package` exits 0; `mvn -q test` (unit, no-DB) exits 0.
Outcome: PASS — executable `sopdemo-backend.jar` built; `HealthControllerTest` green.

### TASK-002 — Safe YAML + Markdown parser + canonical model
Status: COMPLETED
Implements: DES-002, DES-003, FR-020, FR-021, PRN-001, PRN-004, PRN-005, NFR-020
Depends on: TASK-001
Work:
- `domain.model` records matching `spec.md` §4 (Content/Policy/Input/Rule/Condition/Action/Boundaries/Escalation/CustomerMessages/Published) — SNAKE_CASE via Jackson.
- `parse.SafeYaml`: 64KiB guard, anchors/aliases node-identity guard, custom-tag guard, duplicate-key detector, collection nesting ≤20 (root=1), finite numbers, type fidelity.
- `parse.ParsingService`: front matter + seven sections; prose bullets; exactly-one-`yaml`-fence machine blocks; reject unknown/missing/duplicate sections, prose/fence violations.
- `compile.CompileService`: neutral → canonical `Content` with per-field JSON kind + allowed-key-set checks (reject unknown keys) and stable issue paths.
- `PolicyResult` {valid, issues, content}.
- Unit tests: valid template → exact `Content`; hostile YAML (alias, custom tag, dup key, `.nan/.inf`, deep nesting, malformed); section structure (unknown/missing/duplicate, prose, fence); unknown keys, wrong field types, HTML retained as text, stable ordering, determinism.
Verification: `mvn -q -B test` green — 26 tests (SafeYamlTest, ParsingServiceTest, CompileServiceTest, HealthControllerTest), 0 failures.
Outcome: PASS.

### TASK-003 — Validation engine (structural / semantic / financial)
Status: COMPLETED
Implements: DES-004, DES-005, DES-006, FR-030, FR-032, FR-034, PRN-005, NFR-020
Depends on: TASK-002
Work:
- `StructuralValidator` (FR-030): required fields present/nonempty, strings, enums (domain/intent/risk/max_autonomy), `sop_id`+name+action/rule id patterns, unique names/ids, min counts, `domain==Billing` when `refund_duplicate_charge`, non-refund actions have no `max_amount`.
- `ReferenceValidator` (semantic): condition input exists, action_ids exist, op/value type match declared input, bool inputs allow `eq` only, op ∈ {eq,gt,lte}, escalation action_id/target_action_id/op/amount present.
- `FinancialSafety` (FR-032, independent, centralized): refund intent ⇒ exactly one refund + positive `max_amount`; `refund_amount` numeric input; ≥1 escalation boundary {action_id,input,op,amount==limit,target escalate}; emit `FINANCIAL_MISSING_LIMIT` and `FINANCIAL_MISSING_ESCALATION` **independently**; `FINANCIAL_AMOUNT_MISMATCH`; `ANSWER_QUESTION_REFUND` forbids refunds.
- `ValidationService` orchestrates: parse → compile → structural → (semantic refs ∪ financial) ; stable ordering; content null when any issue.
- Unit tests (FR-030/032/034), incl. "separate readable safety issues", semantic-skipped-on-structural-failure, answer_question + no refunds, stability and determinism.
Verification: `mvn -q -B test` green — 58 tests (SafeYamlTest, ParsingServiceTest, CompileServiceTest, HealthControllerTest, StructuralValidatorTest, ReferenceValidatorTest, FinancialSafetyTest, ValidationServiceTest), 0 failures.
Outcome: PASS.

### TASK-004 — Persistence + identity + draft API + seed
Status: COMPLETED
Implements: DES-007/008/010/015, DR-001, DR-003, FR-001, FR-010, PRN-006, PRN-007
Depends on: TASK-003
Work:
- Flyway `V1__init.sql` (`sop_draft`, `sop_publication`, `sop_current`) with `UNIQUE(sop_id,version)` + `UNIQUE(sop_id,draft_revision)`.
- `DraftRepository` (JDBC), `DraftStore` save (upsert, `revision++`, clear failure), idempotent `DemoSeeder` runner.
- `IdentityFilter` (`X-Demo-User` → missing/unknown `401`; author-op consumer `403`) wired for `/api/v1/**`; filter-scope 401 written via the shared envelope.
- `DraftController`: `PUT /drafts/{id}`, `GET /drafts`, `GET /drafts/{id}`; `400` envelope, `413` oversized guards.
- `ApiErrors`: single source of truth for the `{code,message,issues}` envelope **with the correct HTTP status** (IR-001, PRN-003/005), shared by the `@RestControllerAdvice` and the identity filter.
- Integration tests (PostgreSQL): save increments revision; identity 401/403; consumer drafts 403; draft list + `publication_failed`; oversized 413; missing source 400; missing draft 404; `SeedIT` idempotent seed (re-run is a no-op) and never overwrites an existing draft.
Verification: `mvn -q test -Dtest='*IT' -DfailIfNoTests=false` (ephemeral PG) green for draft+identity+seed ITs.
Outcome: PASS — 57 unit tests green; 8 ITs green (DraftApiIT 6, SeedIT 2). Corroborated fixes: error envelope now carries the correct status (previously defaulted to 200 for all API errors); `upsert` `RETURNING` SQL typo fixed; `SeedIT` added for DR-003.

### TASK-005 — Publication service + REST API + error/CORS
Status: COMPLETED
Implements: DES-008/009/011, FR-042, FR-043, FR-045, IR-001, PRN-004, PRN-006
Depends on: TASK-004
Work:
- `PublicationService` (transactional, serialized per `sop_id` by `SELECT ... FOR UPDATE`): locked draft read (404 if absent), revision match (409 stale), re-validate saved source → invalid persists `publication_failed` + 422 while retaining the previous current (FR-045); valid → insert snapshot at `MAX(version)+1` + upsert current + clear indicator in one transaction; documented `sop_id` must equal the draft path else 422 (`SOP_ID_MISMATCH`, IR-001); `UNIQUE(sop_id,version)`/`UNIQUE(sop_id,draft_revision)` backstop → 409.
- `PublicationRepository` (JDBC over `sop_publication`/`sop_current`; JSONB snapshot stored via `CAST(... AS jsonb)`; current/version/list reads); `DraftRepository.findForUpdate` row-lock read.
- `PolicyAuthorController` (`POST /validate` → `{valid,issues,content}` 200; `POST /sops/{id}/publish` 200/404/409/422), `CatalogController` (`GET /sops[?domain&risk]` with 400 on invalid filters, `GET /sops/{id}` detail, `GET /sops/{id}/versions/{v}` author-only).
- Reused the `ApiErrors` envelope/status map; CORS already limited to the configured UI origin.
- Integration tests: AC-E2E-001 (save→validate→publish v1, appears in domain/risk filter + AND-negative), AC-E2E-002 (financial → 422 separate issues → fix → publish), AC-E2E-003 (consumer reads snapshot; consumer mutation 403), AC-E2E-004 (invalid replacement retains v1 → corrected → v2, v1 unchanged), stale 409, duplicate 409, `sop_id` mismatch 422, absent 404, invalid filter 400, missing source 400, concurrent same-revision publish → exactly one winner + a single current version.
Verification: `mvn -q test -Dtest='*IT' -DfailIfNoTests=false` (ephemeral PG) green incl. five journeys' backend behavior.
Outcome: PASS — 57 unit + 18 integration tests green (DraftApiIT 6, SeedIT 2, PublicationIT 10). Corroborated fixes: JSONB insert cast; qualified `sop_current` join columns (ambiguous `version`); atomic publish semantics.

### TASK-006 — Compose stack + deterministic seed + README + demo/down
Status: COMPLETED
Implements: DES-012/015/017, NFR-001, DR-001, DR-003, PRN-007
Depends on: TASK-005
Work:
- `docker-compose.yml` (backend, db with named volume `sopdemo_pgdata` + healthcheck, frontend nginx + `/api` proxy → backend, frontend health) non-default host ports 18080/13000/15432 overridable, `depends_on: service_healthy`, readiness via `up --wait` (no fixed sleeps); overridable `COMPOSE_PROJECT_NAME` for multi-instance runs.
- seed enabled on the demo profile (`SOPDEMO_SEED_ENABLED=true`); backend readiness via `/healthz`.
- README: URLs, identity selection, five journeys, the API, shutdown, data removal, and the port/identity assumptions.
- `Makefile` targets `demo`/`down`/`logs`/`clean` (`clean` = `down -v`, explicit data deletion) with readiness wait.
- Frontend here is a static nginx placeholder that proxies `/api`; the real Vite/React build replaces it in TASK-007 (same `nginx.conf`/compose wiring).
- Verified: 3 healthy services; seed draft present (exact spec §3); author save/validate/publish → v1; consumer reads the same snapshot; AND filters; 401/403/400 contract; restart retains draft + publication; `down -v` removes the volume.
Verification: `docker compose config -q` valid; `make demo` up → 3 healthy; seed retrievable; `docker compose down -v` cleans.
Outcome: PASS — `docker compose config -q` ok; `make demo` → db/backend/frontend all healthy; seed retrievable and published (v1); author→consumer + status contract verified end-to-end; restart retains; `make clean` removes `sopdemo_pgdata`. Frontend healthcheck uses explicit `127.0.0.1` (alpine `localhost`→`::1` quirk).

### TASK-007 — Frontend base: identity selector + list/filters + draft list
Status: COMPLETED
Implements: DES-013/014, FR-001, FR-050, NFR-050
Depends on: TASK-006
Work:
- Vite/React/TS scaffold; `src/App.tsx` shell (identity state + list/editor/detail navigation, one shared `ApiClient`); `src/styles.css` (status/errors conveyed with text, not color alone).
- `src/api.ts` client (sends `X-Demo-User` on every call, normalizes error bodies to `{code,message,issues}`); `src/template.ts` (exact `spec.md` §3 template).
- `IdentitySelector` labeled demo-only; `SopList` with domain/risk AND-filter + author-only draft list (+`publication_failed` flag); consumer sees no draft controls; invalid-filter errors surfaced as text.
- `GET /sops?domain&risk`, `GET /drafts`; 400/401/403 surfaced as text.
- `Editor`/`SopDetail` kept as navigable placeholders (built out in TASK-008/009).
- Frontend `Dockerfile` → multi-stage `npm ci` + `npm run typecheck` + `vite build` → nginx (same `/api` proxy); added `.dockerignore`; removed the static `html/` placeholder.
- Pinned `@testing-library/react` to `^15.0.7` (this registry mirror exposes no 15.2+); committed `package-lock.json` for reproducible builds.
- Vitest + tsc + eslint + build; tests: template==`spec.md`§3 guard, `ApiClient` identity-header + error normalization, `IdentitySelector` (demo-only, radios, `onChange`), `SopList` (empty, domain/risk AND, error-as-text, author-drafts + flag, consumer gating, keyboard-operable controls).
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PASS — lint 0 issues; `tsc --noEmit` clean; 16/16 tests green (template 2, api 4, IdentitySelector 3, SopList 7); `vite build` → 36 modules. Fixed test isolation by registering `@testing-library/react` `cleanup()` in `setup.ts` (Vitest runs with globals disabled). `Editor`/`SopDetail` remain placeholders until TASK-008/009.

### TASK-008 — Editor: template / save / validate / publish
Status: COMPLETED
Implements: DES-013/014, FR-010, FR-034, FR-045, NFR-050
Depends on: TASK-007
Work:
- `Editor` component (wired into `App` for the editor route): loads the draft by `sop_id`, textarea, **Insert template** (exact `spec.md` §3), Save (`PUT /drafts/{id}`), Validate (`POST /validate`), Publish (`POST /sops/{id}/publish`).
- Dirty-state guard (baseline vs `ref` of clean text): Publish disabled until the buffer is saved and clean; an "unsaved changes" indicator (text, not color) shows otherwise; Save **never discards** text on error; Publish uses the saved `revision`.
- Validation issues rendered grouped **structural / semantic** with `code`/`message`/`path`; a persistent failed-publish banner surfaces `publication_failed` (FR-045).
- Consumer: all author actions disabled + a text notice; backend still authoritative.
- Tests: insert → exact template; dirty guard blocks publish then re-enables after save; save-error retains content; structural+semantic issues with paths; publish uses the saved revision; consumer gating; failed-publish surfaced without discarding the draft.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PASS — lint clean; `tsc --noEmit` clean; 23/23 tests green (added 7 Editor tests); `vite build` OK. Editor is wired to the real API; `SopDetail` human/JSON views are next (TASK-009).

### TASK-009 — Human + JSON views (identical snapshot, HTML-as-text)
Status: TODO
Implements: DES-014, FR-052, FR-053, NFR-020
Depends on: TASK-008
Work:
- `HumanView` (policy/inputs/rules/actions/boundaries/messages) + `JsonView`, both from the same fetched `Published` (identity + version shown).
- Raw HTML disabled: rendered as text (no `dangerouslySetInnerHTML`); labels clarify action/message describe an SOP, not a real executed action.
- Consumer-only access; no mutation.
- Tests: human & JSON views show identical `sop_id`/`version` and policy; a `<script>` string appears as literal text (not executed); identity/version present.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PENDING.

### TASK-010 — make verify/smoke/clean + full verification + VERIFICATION.md
Status: TODO
Implements: DES-016/017, NFR-041, PRN-008
Depends on: TASK-009
Work:
- `Makefile` targets: `verify` (backend unit + integration w/ ephemeral PG + frontend test/typecheck/lint/build), `smoke` (compose up → primary author→consumer journey via curl → assert → nonzero on failure), demo/down/logs/clean.
- `smoke.sh` asserts the primary flow end-to-end against the live stack.
- Full `make verify` + `make smoke` run; all green.
- Author `VERIFICATION.md`: requirement coverage matrix, principle adherence, actual command outcomes, design-to-code consistency, known limitations, assumptions.
Verification: `make verify` exit 0; `make smoke` exit 0; `VERIFICATION.md` present and accurate.
Outcome: PENDING.
