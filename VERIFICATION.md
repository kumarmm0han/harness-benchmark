# Verification

Verified on 2026-09-12, Linux arm64, Java 21.0.12, Maven 3.8.7, Node 22.23.2, Docker Engine 29.2.1 and Compose 5.0.2. The database integration tests used PostgreSQL 17.11. Browser journeys used Playwright 1.55.0 / Chromium 140.

Result: all required behavior and all five acceptance journeys passed the checks below. No required implementation work remains deferred. The three frozen inputs are unchanged.

## Requirement coverage

Code paths below are relative to the repository. `CompilerTest` and `ApiIntegrationTest` are under `backend/src/test/java/demo/sop/`; production Java classes are under `backend/src/main/java/demo/sop/`. `UI tests` means `frontend/src/App.test.tsx`; `browser` means `frontend/e2e/journeys.spec.ts`; `smoke` means `scripts/smoke.py`.

| Requirement | Architecture | Design | Tasks | Code | Tests / evidence | Status |
|---|---|---|---|---|---|---|
| FR-001 | ARC-004 | DES-003 | TASK-003, TASK-004 | IdentityFilter.java, App.tsx | ApiIntegrationTest.identityAndMalformedRequests; consumer UI/browser mutation denial | PASS |
| FR-010 | ARC-003, ARC-005 | DES-001, DES-003, DES-004 | TASK-003, TASK-004 | Store.save/draft, Seed.java, editor/template | exact template hash check; API lifecycle; UI save failure; smoke restart/source equality | PASS |
| FR-020 | ARC-002 | DES-002 | TASK-002, TASK-005 | Compiler.java | CompilerTest hostile YAML, structure, aliases, duplicates, collection depth and UTF-8 limits; preview is stateless | PASS |
| FR-021 | ARC-002, ARC-003 | DES-002, DES-003 | TASK-002, TASK-003 | Compiler.java, Store.publish | deterministicCompleteTemplate; preview/published content equality; stored source equality | PASS |
| FR-030 | ARC-002 | DES-002 | TASK-002, TASK-005 | Compiler structural and semantic checks | referencesAndTypes; uniqueness; malformed IDs/types, unknown keys/sections, nonfinancial case | PASS |
| FR-032 | ARC-002 | DES-002 | TASK-002 | Compiler.semantics | independentFinancialFailures; mismatched boundary; refund intent restriction; API and browser rejection/correction | PASS |
| FR-034 | ARC-002, ARC-005 | DES-002, DES-004 | TASK-002, TASK-004, TASK-005 | Issue/Result, App Issues component | sorted stages/paths; original collection indexes; two UI groups; backend rejects invalid publish without preview | PASS |
| FR-042 | ARC-003 | DES-001, DES-003 | TASK-003 | Store.publish, V1__sop.sql | simultaneous requests yield 200/409; forced failure after insert rolls back; stale/duplicate publication; source ID mismatch | PASS |
| FR-043 | ARC-003 | DES-001, DES-003 | TASK-003 | publications table/immutable trigger; historical API | preserved v1 after v2; SQL UPDATE/DELETE rejected; source preserved with snapshot | PASS |
| FR-045 | ARC-003, ARC-005 | DES-003, DES-004 | TASK-003, TASK-004 | failed_revision storage/editor display | failed replacement preserves v1; marker survives restart/reopen; save/success clears marker; unpublished detail 404 | PASS |
| FR-050 | ARC-005 | DES-003, DES-004 | TASK-003, TASK-004 | Store.list/drafts, App filters/lists | API invalid-filter rejection; AND filtering; smoke ascending order; UI empty state and draft discovery | PASS |
| FR-052 | ARC-005 | DES-004 | TASK-004 | Detail.tsx / HumanContent | all canonical sections rendered; HTML-string test and Chromium inert img string; explicit policy-only labels | PASS |
| FR-053 | ARC-005 | DES-003, DES-004 | TASK-003, TASK-004, TASK-005 | current API, Detail shared snapshot | JSON equals human source; no fetch when toggling; browser retains v2 while API publishes v3 | PASS |
| IR-001 | ARC-004 | DES-003 | TASK-003, TASK-005 | Api.java, Errors.java, JsonConfiguration.java, README | all eight operations; strict envelopes/types/duplicate JSON; 400/401/403/404/409/413/422; generic unexpected-error handler reviewed | PASS |
| DR-001 | ARC-003 | DES-001 | TASK-003, TASK-005 | Flyway V1, JDBC transactions, Compose volume | actual empty PostgreSQL migration; row/constraint behavior; failed transaction rollback; two persistence restarts | PASS |
| DR-003 | ARC-001 | DES-001 | TASK-003, TASK-005 | Seed.java demo profile, exact template | seedPreservesEditsAndIdMismatch; clean seed revision 1; seed unchanged across restarts | PASS |
| NFR-001 | ARC-001 | DES-001, DES-005 | TASK-005, TASK-006 | Dockerfiles, compose.yaml, README | clean remote checkout: make demo -> exactly 3 healthy services and new named volume; make smoke -> restarts retained data | PASS |
| NFR-020 | ARC-002, ARC-004 | DES-002, DES-003, DES-004 | TASK-002–TASK-005 | safe compiler, identity filter, React text, controlled errors | hostile YAML/HTML; server UTF-8 limits; role denial; local CORS; no executable authored-content path or real credentials found in code review | PASS |
| NFR-041 | ARC-006 | DES-005 | TASK-002–TASK-006 | Makefile, test suites, smoke scripts | make verify/demo/smoke all exit 0, including real PostgreSQL and real Chromium | PASS |
| NFR-050 | ARC-005 | DES-004 | TASK-004, TASK-005 | labeled native controls, text status/errors | keyboard template activation in Chromium; labeled forms; unsaved state; save-error retention; readable staged errors | PASS |
| AC-E2E-001 | ARC-002, ARC-003, ARC-005 | DES-002–DES-005 | TASK-002–TASK-006 | author/save/preview/publish/filter flow | API smoke plus Chromium saves and publishes v1, then filters Billing/medium | PASS |
| AC-E2E-002 | ARC-002, ARC-003, ARC-005 | DES-002–DES-005 | TASK-002–TASK-006 | centralized financial checks and error UI | separate REFUND_LIMIT/REFUND_ESCALATION issues; rejected saved candidate; both restored and publication succeeds | PASS |
| AC-E2E-003 | ARC-004, ARC-005 | DES-003–DES-005 | TASK-003–TASK-006 | consumer API and shared snapshot detail | real browser human/JSON equality and concurrent newer-publication isolation; direct consumer mutation 403 | PASS |
| AC-E2E-004 | ARC-003, ARC-005 | DES-003–DES-005 | TASK-003–TASK-006 | transactional failure marker/version history | saved invalid replacement leaves v1; reopened failure shown; corrected revision yields v2; v1 exactly unchanged | PASS |
| AC-E2E-005 | ARC-001, ARC-003, ARC-006 | DES-001, DES-005 | TASK-005, TASK-006 | full Compose and seed/persistence | clean GitHub checkout/new volume, complete author/consumer flow, 3 healthy services after both restarts, saved source and snapshots unchanged | PASS |

## Actual command outcomes

All final commands below completed with exit code 0. Earlier failed checks and corrections are recorded separately; they are not counted as passing runs.

| Command | Actual outcome |
|---|---|
| `python3 scripts/check-inputs.py` | SHA-256 matches for PRINCIPLES.md, REQUIREMENTS.md, spec.md; backend/frontend templates exactly match the frozen example |
| `make verify` | Compose config valid; Maven build success; **10 compiler tests + 4 PostgreSQL integration tests**, 0 failures/errors/skips; **6 frontend tests** passed; TypeScript, ESLint and Vite production build passed |
| `make demo` | Both application images built; PostgreSQL, backend, frontend all healthy; named volume persisted between runs |
| `make smoke` | All five API journey assertions passed; two successful Compose restarts; **2 Chromium browser tests** passed, including combined AC-E2E-001–004 and seed discovery |
| `git clone --single-branch --branch codex-01-sop-demo https://github.com/kumarmm0han/harness-benchmark.git .verification/checkout` | Clean clone of pushed runtime commit **c5763c8c2fbed85fa43173850905b456c102e564** |
| In clean checkout: `COMPOSE_PROJECT_NAME=codex01-cleancheck UI_PORT=5174 API_PORT=8081 make verify` | All 14 backend + 6 frontend tests, types, lint and build passed again from freshly installed dependencies |
| In clean checkout with those same variables: `make demo` | New `codex01-cleancheck_sop-data` volume and exactly three healthy services; deterministic seed available |
| In clean checkout with those same variables: `make smoke` | API acceptance and both restart sequences passed for `SMOKE-68D8E845C5884C27`; both Chromium tests passed (2.7 seconds) |
| `test -z "$(git status --porcelain)"` in clean checkout, before and after audit | Both passed; verification did not modify tracked files |
| `git diff --check` | No whitespace errors |

Full local logs are retained in ignored `.verification/verify.log`, `demo.log`, `smoke.log`, `clean-verify.log`, `clean-demo.log`, `clean-smoke.log`, and `clean-services.json`. They are not required to build or run the app. From the user's directory instruction onward, all agent-created verification files stayed inside this repository. The final report/tracker commit changes documentation only relative to the tested runtime commit.

The test runner emits non-failing Mockito agent/JVM warnings, npm deprecation notices for ESLint/whatwg-encoding, and Playwright terminal color warnings. None caused a skipped or failed final check. No separate Java formatter/static-analysis task existed; Java compilation and behavioral tests passed, and TypeScript/ESLint provide frontend static checks.

## Principle adherence

| Principle | Evidence |
|---|---|
| PRN-001 | One compiler yields canonical content; original source and full snapshot are stored together. Human and JSON render one fetched envelope; no separately editable consumer policy exists. |
| PRN-002 | All five small journeys completed. Only Billing/Support, low/medium, assist, and the documented intents/fields are accepted. No rule engine or external action was added. |
| PRN-003 | Exactly React/TypeScript, Java 21/Spring Boot, and PostgreSQL run in Compose. Compiler/API/JDBC remain in one backend deployment. Browser/test containers are verification tooling, not runtime services. |
| PRN-004 | Backend identity/authorization, safe parsing and validation are authoritative. Authored strings never become executable code or HTML. CORS is restricted to the configured local UI origin. |
| PRN-005 | Ordered issue records include stage/code/message/path; complete mapping is deterministic; financial checks are centralized and independently tested. Unknown/ambiguous content is rejected. |
| PRN-006 | Row locks, transactions, uniqueness/FK constraints and immutable-row triggers preserve version integrity. Forced post-insert failure and concurrent publication were tested. Validation rejection persists its marker without changing current. |
| PRN-007 | Clean-source builds, migrations, readiness checks, named volume and non-overwriting demo seed were tested. README documents startup, tools, shutdown and explicit data removal. |
| PRN-008 | Final evidence includes real PostgreSQL, real Chromium, hostile inputs, direct permissions, rollback/concurrency, consumer consistency, clean checkout and restart persistence. Legitimate assertions were retained through corrections. |
| PRN-009 | Architecture/design/tasks reference frozen IDs. Every task completion was verified and pushed before its successor began. The final audit was split into TASK-006 to inspect an already pushed runtime. Frozen documents were never edited. |

## Design-to-code consistency and recovery

The implemented components and transaction boundaries match ARC-001–006 and DES-001–005. Direct JDBC and a single compiler/backend avoid speculative abstractions. Material scope was not expanded.

Corrections made during implementation:

- TASK-001's reference assertion initially rejected abbreviated acceptance IDs. Explicit mappings were added; the completion correction was verified, committed and pushed before TASK-002. The initial artifact-only commit preceded that completion correction.
- TASK-002 initially called an unavailable SnakeYAML method; the unnecessary standard-tag check was removed while the supported-tag allowlist and safe constructor remained. A boundary mismatch fixture initially replaced both numbers; narrowing the fixture retained the intended mismatch assertion.
- TASK-003's malformed-envelope test exposed Jackson number-to-string coercion. Explicit textual coercion rejection was added, and the full PostgreSQL suite passed.
- TASK-005's first browser test used an exact label locator that included nested select text. The accessible combobox-name locator passed with the intended filter assertions intact.
- Final review retained original collection indexes after malformed entries and mapped unsupported media types to controlled 400 responses. Regression tests and the full verification suite passed.
- Playwright was added to supplement component/API checks with real UI evidence. Final clean-checkout reporting moved from TASK-005 to TASK-006 so its input could be a confirmed pushed commit. These refinements are recorded in TECHNICAL_DESIGN.md and TASKS.md.

## Assumptions

1. CRLF is accepted while parsing, but saved and published source text is retained exactly. Plain Markdown bullets use `- ` or `* `; no prose formatting is interpreted.
2. Draft path IDs use the specified SOP ID syntax even for incomplete source. Authors creating another SOP change both the path ID and source ID.
3. The persisted publication-failure indicator is nullable `failed_revision`; author responses also expose nullable `current_version` to describe retained availability.
4. Missing refund max_amount is a semantic issue so missing escalation can be reported independently. Dependent semantic checks are skipped after structural failures, as permitted.
5. Identity is required on actual API operations. Standard CORS preflight and service health probes are unauthenticated; all actual protected requests still check identity.
6. Static nginx serving/proxying is part of the React service. It adds no fourth runtime deployment. Compose defaults use localhost ports 5173/8080 and a fictional local database password.
7. The read-only consumer UI retains its fetched version until the user loads another snapshot; switching its human/JSON view never refetches. The browser test explicitly verifies this while another author publishes a newer version.

## Known limitations

- Verification was performed on Linux arm64 and Chromium. Other operating systems and browsers were not exercised.
- Browser seed validation expects the original valid seed in a fresh demo database. Startup and tests never overwrite a user-edited seed; the clean-checkout audit used a fresh volume.
- Fixed header identities and local defaults are intentionally demo-only. Production authentication/deployment and execution of authored actions are outside the frozen scope.
- Initial builds/browser setup require dependency and image downloads. The running three-service application uses no external services.

## Git delivery

Working branch: `codex-01-sop-demo`; destination: `origin` at the GitHub repository above. Confirmed successful pushes before starting each next task:

| Task | Delivered commit(s) |
|---|---|
| TASK-001 | c86e0a0 initial artifacts, then 45a2eda verified completion correction |
| TASK-002 | 29f2917 |
| TASK-003 | 83ab7dc |
| TASK-004 | 22bf5b8 |
| TASK-005 | c5763c8 |
| TASK-006 | This final report/tracker commit; final push result is checked after commit creation and reported to the user |

No force-push, history overwrite, input modification, or unresolved delivery failure occurred.
