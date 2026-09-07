# TASKS

Task tracker for the SOP demo. Statuses are updated during implementation. Priorities:
`PRINCIPLES.md` > `REQUIREMENTS.md` > `ARCHITECTURE.md` > `TECHNICAL_DESIGN.md` > this file.
Each task carries a verification criterion; mark `COMPLETED` only after that criterion passes.

| ID | Status | Implements | Depends on | Focus |
|---|---|---|---|---|
| TASK-001 | COMPLETED | ARC-001, PRN-003, PRN-007 | — | backend scaffold + build |
| TASK-002 | COMPLETED | DES-002/003, FR-020/021, PRN-001/004/005, NFR-020 | 001 | safe YAML + Markdown parse + canonical model |
| TASK-003 | COMPLETED | DES-004/005/006, FR-030/032/034, PRN-005, NFR-020 | 002 | validation engine (structural/semantic/financial) |
| TASK-004 | IN_PROGRESS | DES-007/008/010/015, DR-001/003, FR-001/010, PRN-006/007 | 003 | persistence + identity + draft API + seed |
| TASK-005 | TODO | DES-008/009/011, FR-042/043/045, IR-001, PRN-004/006 | 004 | publication service + REST API + error/CORS |
| TASK-006 | TODO | DES-012/017, NFR-001, DR-001/003, PRN-007 | 005 | compose stack + seed + README + demo/down |
| TASK-007 | TODO | DES-013/014, FR-001/050, NFR-050 | 006 | frontend base: identity/list/filters/drafts |
| TASK-008 | TODO | DES-013/014, FR-010, NFR-050 | 007 | editor: template/save/validate/publish |
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
Status: IN_PROGRESS
Implements: DES-007/008/010/015, DR-001, DR-003, FR-001, FR-010, PRN-006, PRN-007
Depends on: TASK-003
Work:
- Flyway `V1__init.sql` (`sop_draft`, `sop_publication`, `sop_current`) with `UNIQUE(sop_id,version)` + `UNIQUE(sop_id,draft_revision)`.
- `DraftRepository` (JDBC), `DraftStore` save (upsert, `revision++`, clear failure), idempotent `DemoSeeder` runner.
- `IdentityFilter` (`X-Demo-User` → missing/unknown `401`; author-op consumer `403`) wired for `/api/v1/**`.
- `DraftController`: `PUT /drafts/{id}`, `GET /drafts`, `GET /drafts/{id}`; `400` envelope, `413` oversized guards.
- Integration tests (PostgreSQL): save increments revision; restart preserves; identity 401/403; consumer drafts 403; draft list shows indicator; seeder idempotent (run twice).
Verification: `mvn -q test -Dtest='*IT' -DfailIfNoTests=false` (ephemeral PG) green for draft+identity+seed ITs.
Outcome: PENDING.

### TASK-005 — Publication service + REST API + error/CORS
Status: TODO
Implements: DES-008/009/011, FR-042, FR-043, FR-045, IR-001, PRN-004, PRN-006
Depends on: TASK-004
Work:
- `PublicationService`: locked draft read, revision match (409), re-validate; invalid → set `publication_failed` + 422; valid → insert `max(version)+1` + upsert current (one transaction); constraint violations → 409.
- `PolicyAuthorController` (`POST /validate`, `POST /sops/{id}/publish`), `CatalogController` (`GET /sops[?domain&risk]`, `/sops/{id}`, `/sops/{id}/versions/{v}`).
- `ApiErrors` `@ControllerAdvice` with `{code,message,issues}` and the full status map (400/401/403/404/409/413/422/500); CORS to configured UI origin only.
- Integration tests: AC-E2E-001 (save→validate→publish v1→filter), AC-E2E-002 (financial → 422 → fix → publish), AC-E2E-003 (consumer reads same snapshot + author-op 403), AC-E2E-004 (invalid replacement retains v1 → corrected → v2, v1 unchanged), AC-E2E-005 (seed + retain), concurrency (2 revisions → distinct versions; duplicate → 409; stale → 409); 404 absent; 400 bad filter.
Verification: `mvn -q test -Dtest='*IT' -DfailIfNoTests=false` (ephemeral PG) green incl. five journeys' backend behavior.
Outcome: PENDING.

### TASK-006 — Compose stack + deterministic seed + README + demo/down
Status: TODO
Implements: DES-012/015/017, NFR-001, DR-001, DR-003, PRN-007
Depends on: TASK-005
Work:
- `docker-compose.yml` (backend, db with named volume `sopdemo_pgdata` + healthcheck, frontend nginx + `/api` proxy → backend, frontend health) non-default host ports 18080/13000/15432 overridable, `depends_on: service_healthy`, readiness check (not sleep-based).
- seed enabled on demo profile; `backend` readiness check.
- README: URLs, identity selection, five journeys, shutdown, data removal, and the port/identity assumptions.
- `Makefile` targets `demo`/`down`/`logs`/`clean` (removes the named volume — explicit data deletion) with readiness wait.
- Verified compose up brings 3 healthy services + seed present + restart retains drafts and publications.
Verification: `docker compose config -q` valid; `make demo` up → 3 healthy; seed retrievable; `docker compose down -v` cleans.
Outcome: PENDING.

### TASK-007 — Frontend base: identity selector + list/filters + draft list
Status: TODO
Implements: DES-013/014, FR-001, FR-050, NFR-050
Depends on: TASK-006
Work:
- Vite/React/TS scaffold; `src/api.ts` client (sends `X-Demo-User`, normalizes error bodies to `{code,message,issues}`); `src/template.ts` (exact `spec.md` §3 template).
- `IdentitySelector` labeled demo-only; `SopList` with domain/risk filter (AND) + author-only draft list; consumer sees no draft controls; error/filter-invalid shown as text (not color).
- `GET /sops?domain&risk`, `GET /drafts`; 400 on invalid filter surfaced.
- Vitest + tsc + eslint + build; tests: filter wiring, role gating of draft list, empty state, error text, keyboard usable.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PENDING.

### TASK-008 — Editor: template / save / validate / publish
Status: TODO
Implements: DES-013/014, FR-010, NFR-050
Depends on: TASK-007
Work:
- `Editor` component: textarea, insert-template, save (`PUT /drafts/{id}`), validate (`POST /validate` → issues grouped structural/semantic with paths), publish (`POST /sops/{id}/publish`).
- Dirty-state guard: publish disabled until saved; unsaved-changes indicator; save errors surface **without discarding** text; publish uses saved revision.
- Consumer: author controls disabled; text-based status.
- Tests: insert inserts exact `spec.md` template; dirty guard blocks publish; save-error retains content; validate shows both stages; publish uses revision.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PENDING.

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
