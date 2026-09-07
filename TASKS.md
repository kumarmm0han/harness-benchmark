# TASKS

Task tracker for the SOP demo. Maintain status during implementation. Priorities:
`PRINCIPLES.md` > `REQUIREMENTS.md` > `ARCHITECTURE.md` > `TECHNICAL_DESIGN.md` > this file.
Each task carries a verification criterion; mark `COMPLETED` only after it passes.

| ID | Status | Implements | Depends on | Focus |
|---|---|---|---|---|
| TASK-001 | COMPLETED | ARC-001, PRN-003, PRN-007 | — | backend scaffold + build |
| TASK-002 | TODO | DES-002/003, FR-020/021, PRN-001/004/005, NFR-020 | 001 | safe YAML + Markdown parse + canonical model |
| TASK-003 | TODO | DES-004/005/006, FR-030/032/034, PRN-005, NFR-020 | 002 | validation engine (structural/semantic/financial) |
| TASK-004 | TODO | DES-007/008/010/015, DR-001/003, FR-001/010, PRN-006/007 | 003 | persistence + identity + draft API + seed |
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
- `health` endpoint; verify `mvn -q -DskipTests package` yields an executable jar.
Verification: `mvn -q -DskipTests package` exits 0; `mvn -q test` (unit, no-DB) exits 0.
Outcome: PASS — jar builds and runs; unit profile loads without a DB.

### TASK-002 — Safe YAML + Markdown parser + canonical model
Status: COMPLETED
Implements: DES-002, DES-003, FR-020, FR-021, PRN-001, PRN-004, PRN-005, NFR-020
Depends on: TASK-001
Work:
- `domain.model` records matching `spec.md` §4 exactly (Content/Policy/Input/Rule/Condition/Action/Boundaries/Escalation/CustomerMessages/Published).
- `parse.SafeYaml`: byte limit, anchors/aliases token guard, SafeConstructor (custom tags), duplicate-key detector, nesting ≤20, finite numbers, type fidelity.
- `parse.MarkdownDoc`: front matter + seven sections; prose bullets; exactly-one-`yaml`-fence machine blocks; reject unknown sections/structure; 64KiB.
- `CompilationService` → canonical `Content` (no invented fields, order preserved).
- Unit tests: valid template parses to exact shape; hostile YAML (alias, custom tag, dup key, `.nan`, deep nesting), HTML-in-text treated as data, unknown section, missing section, extra fence, oversize.
Verification: `mvn -q test -Dtest='Parsing*,SafeYaml*,Compilation*'` green incl. hostile cases.
Outcome: PASS.

### TASK-003 — Validation engine (structural / semantic / financial)
Status: COMPLETED
Implements: DES-004, DES-005, DES-006, FR-030, FR-032, FR-034, PRN-005, NFR-020
Depends on: TASK-002
Work:
- `issue.ValidationIssue` {code,stage,message,path}, stable sort (path then code).
- Structural checks (front-matter required/enum/pattern/unique/empty), input/rule/action field+type+id+uniqueness+min counts, unknown-key (via parse).
- Semantic checks (run only if structural clean): input/action references, op/value type match, bool `eq`-only, op set.
- `FinancialSafety` (independent unit): refund intent ⇒ exactly one refund+positive `max_amount`, `refund_amount` number input, ≥1 escalation boundary {action_id,input,op,amount==limit,target escalate}; `FINANCIAL_MISSING_LIMIT` vs `FINANCIAL_MISSING_ESCALATION` distinct; `ANSWER_QUESTION_REFUND` forbids refunds.
- Unit tests cover FR-030/032/034 incl. "separate readable safety issues", determinism, semantic-skipped-on-structural-failure.
Verification: `mvn -q test -Dtest='Validation*,Financial*'*` green; determinism assertion holds.
Outcome: PASS.

### TASK-004 — Persistence + identity + draft API + seed
Status: COMPLETED
Implements: DES-007/008/010/015, DR-001, DR-003, FR-001, FR-010, PRN-006, PRN-007
Depends on: TASK-003
Work:
- Flyway `V1__init.sql` (sop_draft/sop_publication/sop_current + UNIQUE(sop_id,version)+UNIQUE(sop_id,draft_revision)).
- `DraftRepository` (JDBC), `DraftStore` save (upsert, `revision++`, clear failure), idempotent seeder.
- `IdentityFilter` (missing/unknown → 401; author-op consumer → 403), wired for `/api/v1/**`.
- `DraftController`: `PUT /drafts/{id}`, `GET /drafts`, `GET /drafts/{id}`; 400/413 guards.
- Integration tests (PostgreSQL): save increments revision, restart preserves (separate contexts share DB), identity 401/403, consumer draft 403, draft list shows indicator, seeder idempotent (run twice).
Verification: `mvn -q verify -Dgroups=integration` (ephemeral PG) green for draft+identity+seed.
Outcome: PASS.

### TASK-005 — Publication service + REST API + error/CORS
Status: COMPLETED
Implements: DES-008/009/011, FR-042, FR-043, FR-045, IR-001, PRN-004, PRN-006
Depends on: TASK-004
Work:
- `PublicationService`: locked read-draft, revision match (409), re-validate; invalid → set failure + 422; valid → insert version=max+1 + upsert current (one txn); constraint violations → 409.
- `PolicyAuthorController` (`POST /validate`, `POST /sops/{id}/publish`), `CatalogController` (`GET /sops[?&]`, `/sops/{id}`, `/sops/{id}/versions/{v}`).
- `ApiErrors` @ControllerAdvice with `{code,message,issues}` and the full status map (400/401/403/404/409/413/422/500); CORS to configured UI origin.
- Integration tests: AC-E2E-001 save→validate→publish(v1)→list filter; AC-E2E-002 financial issues→422→fix→publish; AC-E2E-003 consumer reads same snapshot + author-op 403; AC-E2E-004 invalid-replacement retains v1 + corrected→v2 + v1 unchanged; concurrent publish (two revisions→distinct versions, duplicate→409); stale→409; 404 absent; 400 bad filter.
Verification: `mvn -q verify -Dgroups=integration` green for the above incl. all five journeys' backend behavior.
Outcome: PASS.

### TASK-006 — Compose stack + deterministic seed + README + demo/down
Status: COMPLETED
Implements: DES-012/015/017, NFR-001, DR-001, DR-003, PRN-007
Depends on: TASK-005
Work:
- `docker-compose.yml` (backend, db with named volume `sopdemo_pgdata` + healthcheck, frontend later) with env, ports 18080/15432 (env-overridable), `depends_on: service_healthy`.
- seed enabled on demo profile; `backend` readiness check.
- README: URLs, identity selection, five journeys, shutdown, data removal.
- `Makefile` targets `demo`/`down`/`logs`/`clean` (remove volume), readiness wait.
- Compose `db` + `backend` up; seed present; restart retains drafts (journey AC-E2E-005 backend half).
Verification: `docker compose config` valid; `make demo` brings up healthy backend+db; seed draft retrievable; restart retains.
Outcome: PASS.

### TASK-007 — Frontend base: identity selector + list/filters + draft list
Status: COMPLETED
Implements: DES-013/014, FR-001, FR-050, NFR-050
Depends on: TASK-006
Work:
- Vite/React/TS scaffold; `src/api.ts` client (X-Demo-User, error normalization); `src/template.ts`.
- `IdentitySelector` (demo-only labeled), `SopList` (domain/risk filter form, AND, sop_id asc, empty state), author-only draft list (consumer sees none).
- Access: `GET /sops?domain&risk`, `GET /drafts`; 400 on invalid filter surfaced as text.
- Vitest + tsc + eslint + build; tests: filter wiring, role gating of draft list, empty state, error text.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PASS.

### TASK-008 — Editor: template / save / validate / publish
Status: COMPLETED
Implements: DES-013/014, FR-010, NFR-050
Depends on: TASK-007
Work:
- `Editor` component: textarea, insert-template, save (`PUT /drafts/{id}`), validate (`POST /validate` → issues grouped structural/semantic w/ paths), publish (`POST /sops/{id}/publish`).
- Dirty-state guard: publish disabled until saved; unsaved changes indicator; save errors surface **without discarding** text; publish uses saved revision.
- Consumer: author controls disabled; text-based status (not color alone).
- Tests: insert inserts exact `spec.md` template; dirty guard blocks publish; save-error retains content; validate shows both stages; publish uses revision.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PASS.

### TASK-009 — Human + JSON views (identical snapshot, HTML-as-text)
Status: COMPLETED
Implements: DES-014, FR-052, FR-053, NFR-020
Depends on: TASK-008
Work:
- `HumanView` (policy/inputs/rules/actions/boundaries/messages) + `JsonView`, both from the same fetched `Published` (identity + version shown).
- Render authored strings as text only (no `dangerouslySetInnerHTML`); labels clarify action/message describe an SOP, not a real executed action.
- Consumer-only access; no mutation.
- Tests: human & JSON views show **identical** `sop_id`/`version` and policy; an authored HTML `<script>` string appears as literal text (not executed); identity/version present.
Verification: `npm run lint && npm run typecheck && npm run test && npm run build` green.
Outcome: PASS.

### TASK-010 — make verify/smoke/clean + full verification + VERIFICATION.md
Status: COMPLETED
Implements: DES-016/017, NFR-041, PRN-008
Depends on: TASK-009
Work:
- `Makefile` `verify` (backend unit+integration w/ ephemeral PG, frontend test/typecheck/lint/build), `smoke` (Compose up → primary author→consumer journey via curl → assert → teardown-safe; nonzero on failure), `clean`.
- `smoke.sh` asserts five-journey-relevant flow end-to-end on the live stack.
- Run `make verify`, `make smoke`; confirm all green.
- Write `VERIFICATION.md`: requirement coverage matrix, principle adherence, actual command outcomes, design-to-code consistency, known limitations, assumptions.
Verification: `make verify` exit 0; `make smoke` exit 0; `VERIFICATION.md` present and accurate.
Outcome: PASS.
