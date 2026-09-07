---
Artifact: VERIFICATION.md
Stage: 6 (of 6) — final verification
Status: complete
Working branch: ft-opencode-01 → origin (github.com/kumarmm0han/harness-benchmark)
Final commit delivered: 1fc7b6c (confirmed via `git ls-remote origin ft-opencode-01`)
---

# SOP Demo — Verification

Frozen inputs: `PRINCIPLES.md` (PRN-001…009), `REQUIREMENTS.md` (FR-*/IR-001/
DR-001·003/NFR-001·020·041·050, AC-E2E-001…005), `spec.md`. All three were
treated as unchanged. Traceability: ARCH-*/DES-* → code → tests below.

## 1. Requirement coverage

| Requirement | Architecture | Design | Task(s) | Code | Tests | Status |
|---|---|---|---|---|---|---|
| FR-001 (identity + perms) | ARC-002 | DES-007 | TASK-010 | IdentityFilter, Role | IdentityTest (4) | PASS |
| FR-010 (author/persist) | ARC-007 | DES-008a | TASK-009, 017 | DraftService, Editor | DraftApiTest (5), editor.test (unsaved guard) | PASS |
| FR-020 (safe parse/preview) | ARC-006 | DES-003 | TASK-004, 011 | SafeYaml, ValidateApiTest | SafeYamlTest (12), ValidateApiTest (6) | PASS |
| FR-021 (canonical compile) | ARC-006 | DES-005,006 | TASK-008 | CanonicalBuilder, PublishService | PipelineValidationTest; Publish sop_id match | PASS |
| FR-030 (structural/reference) | ARC-006 | DES-005 | TASK-006 | FrontMatter/Shape/Ref validators | PipelineValidationTest, FinancialValidationTest | PASS |
| FR-032 (financial safety) | ARC-005 | DES-005 | TASK-007 | FinancialValidator | FinancialValidationTest (11) + ValidateApiTest | PASS |
| FR-034 (validation feedback) | ARC-006 | DES-009 | TASK-008, 011 | Pipeline, Validate endpoint | PipelineValidationTest, ValidateApiTest (incl. FIN_* both) | PASS |
| FR-042 (atomic publication) | ARC-008 | DES-008b | TASK-012 | PublishService.commitPublish | PublishAndReadTest (10, incl. concurrency) | PASS |
| FR-043 (immutable versions) | ARC-008,010 | DES-008c | TASK-012, 013 | PublicationRepository, GetVersion | historicalVersionImmutable, publishersOfDifferentRevisions | PASS |
| FR-045 (preserve previous) | ARC-007,008 | DES-008a,b | TASK-012 | PublishService.recordPublishFailure | ac004InvalidThenFixed, seedPublishesNothing | PASS |
| FR-050 (list + filter) | ARC-009 | DES-008c,009 | TASK-013 | PublicApi.list | listFilters, SopList/FilterBar tests | PASS |
| FR-052 (human view) | ARC-001 | DES-012 | TASK-018 | HumanView | views.test (incl. XSS, disclaimers) | PASS |
| FR-053 (AI/JSON view) | ARC-001 | DES-012 | TASK-018 | JsonView, DetailPane | views.test (same snapshot) | PASS |
| IR-001 (8 endpoints + codes) | — | DES-009 | TASK-010,011,012,013 | AuthorApi/PublicApi, GlobalExceptionHandler | all API tests | PASS |
| DR-001 (PostgreSQL persist) | ARC-007,008,012 | DES-002 | TASK-001,009,012 | Flyway V1, JPA | AbstractPostgresTest (real PG 16) | PASS |
| DR-003 (deterministic seed) | ARC-011 | DES-010 | TASK-014 | SeedInitializer | SeedInitializerTest (4) | PASS |
| NFR-001 (reproducible demo) | ARC-013 | DES-013 | TASK-002,003 | compose, Dockerfiles, Makefile | `make demo` healthy; smoke | PASS |
| NFR-020 (content/config safety) | ARC-004,015 | DES-003,011 | TASK-004,010,018 | SafeYaml, filter, React escaping | SafeYamlTest, IdentityTest, views XSS | PASS |
| NFR-041 (focused verification) | — | DES-014 | TASK-021 | Makefile `verify`/`smoke` | 82 backend + 23 frontend + smoke | PASS |
| NFR-050 (demo usability) | ARC-001 | DES-012 | TASK-015,016,017 | App, panels, toasts | list-filter, editor, client tests | PASS |
| PRN-001…009 (principles) | all | all | all | — | — | PASS (see §2) |

Every enumerated FR/IR/DR/NFR/AC-E2E id and PRN is covered. No requirement was
weakened or removed.

## 2. Principle adherence

- **PRN-001 one canonical source** — Publication stores `content_json` (canonical)
  + `source`; `sop_current` points to it; Human + JSON views render one fetched
  `Envelope` object (`DetailPane.detail`). No parallel editable policy stores.
- **PRN-002 complete small journey, only required scope** — Authoring → validate →
  publish → both views implemented; no router, no auth stack beyond the two fixed
  identities, low/medium + assist-only enforced (rejects otherwise). No speculative
  extra endpoints.
- **PRN-003 three-service local runtime** — `compose/docker-compose.yml` with exactly
  db/backend/frontend; one backend deployment holds parse/validate/publish/query;
  deps limited to Spring Boot BOM, SnakeYAML, Flyway, JPA.
- **PRN-004 backend authority + untrusted content** — `IdentityFilter`/`Role`
  enforce 401/403 server-side on all 8 endpoints (UI hiding is cosmetic);
  `SafeYaml` blocks aliases/tags/depth/non-finite/dup-keys; rules/messages are
  never executed; React escapes all authored strings (XSS test proves it);
  `GlobalExceptionHandler` never leaks SQL/stack/internals on 5xx.
- **PRN-005 deterministic, readable validation** — pipeline emits stable, sorted,
  deduped `(path,code)` issues; identical source ⇒ identical content+issues
  (asserted in PipelineValidationTest); financial checks centralized in
  `FinancialValidator`, independently tested; limit and escalation reported separately.
- **PRN-006 atomic publication + preserved history** — re-validate the exact saved
  revision; `pg_advisory_xact_lock(hashtext(sop_id))` + unique `(sop_id,version)` and
  `(sop_id,draft_revision)` constraints; concurrency test yields exactly 1 success +
  3×409 and 1 row; invalid publish sets the persisted indicator and leaves current
  unchanged.
- **PRN-007 reproducible local delivery** — one `make demo` starts 3 healthy services
  (healthchecked readiness, no fixed sleeps); Flyway migrations + named volume
  `sop_pgdata`; idempotent seed preserving edits; README documents startup/smoke/
  verification/shutdown and the explicit `make down-clean` deletion.
- **PRN-008 evidence proportional to scope** — real-Postgres integration tests,
  hostile YAML + hostile HTML coverage, permission/version/consistency tests;
  `make verify` and `make smoke` recorded; no test deleted or assertion weakened to
  pass (the one red backend test was a genuine design bug, fixed by split
  transactions — see §4).
- **PRN-009 traceable implementation** — architecture/design/task ids referenced in
  code comments and tests; TASKS.md kept current, each task marked COMPLETED during
  implementation and committed+pushed by task id; delivery confirmed via
  `git ls-remote`. Design drift during implementation is recorded in §4.

## 3. Test / build results (actual commands and outcomes)

| Command | Outcome |
|---|---|
| `cd backend && mvn -q -f pom.xml test` | **Tests run: 82, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (unit + real PostgreSQL 16 via docker) |
| `cd frontend && npx tsc --noEmit` | clean (0 errors) |
| `cd frontend && npm run lint` | clean (0 errors, 0 warnings, `--max-warnings 0`) |
| `cd frontend && npx vitest run` | **Test Files: 4 passed, Tests: 23 passed** (client, list/filter, editor, views) |
| `cd frontend && npm run build` | `vite build` → `built in ~450ms`, `dist/index.html` + JS 161.6 kB |
| `docker compose -f compose/docker-compose.yml build` | all images built (postgres:16-alpine pre-cached) |
| `make demo` (clean-slate) | db, backend, frontend all **healthy** |
| `make smoke` (clean-slate) | **SMOKE PASS** — save→validate→publish(v1)→list(Billing/medium)→consumer detail → consumer 403 |
| AC-E2E-005 restart | `make down` (keep volume) + `make demo` again → draft (rev 2) and publication (v1) retained, seed not re-duplicated |
| `make verify` | **verify: OK** (backend 82/82 + frontend 23/23 + tsc + eslint + vite build) |

Test breakdown: backend unit — SafeYaml (12), SectionParser (9), Pipeline/Shape/
Reference/Financial (multiple), FrontMatter; backend integration — DraftApi (5),
Identity (4), Validate (6), PublishAndRead (10), Seed (4). Frontend — client (6),
list/filter (6), editor (5 incl. unsaved guard + template byte-equality), views (6
incl. XSS + shared-snapshot + disclaimers).

## 4. Design-to-code consistency (material deviations)

1. **Publish transaction split (TASK-012; recorded in TECHNICAL_DESIGN.md DES-008b).**
   The design described `publish()` as one `@Transactional` method with the FR-045
   failure indicator written on the same "read" path. A single transaction that throws
   422 rolls the indicator back — so AC-E2E-004 could not be satisfied (the indicator
   would disappear). Implementation splits it into `publish()` (orchestration),
   `recordPublishFailure()` (own committed transaction so the FR-045 indicator
   **persists**), and `commitPublish()` (single atomic transaction for the actual
   publication writes). The FR-042 atomicity guarantee for successful publication is
   unchanged; the indicator record is an independent commit, which FR-045 ("persist a
   failure indicator") requires. This is a correction of an ambiguous design line, not
   a weakening.
2. **`jsonb` → `text` for stored snapshots (DES-002 migration).** The migration stores
   `content_json`/`envelope_json` as `text` and the JPA `columnDefinition` matches;
   identical content is serialized/deserialized with Jackson. Semantically equivalent
   for "canonical JSON snapshot stored with its source" (FR-021); `text` avoids
   driver-dependent jsonb typing and keeps the round-trip exact. Not a requirement change.
3. **Issue code `ALIAS` vs design table wording `PARSE_FAILED`.** DES-001's table
   groups "unknown tag/alias" under `PARSE_FAILED`; the code emits the dedicated,
   more precise `ALIAS` code (defined in `Contract.C_ALIAS`) for alias rejection, while
   true parse failures still map to `PARSE_FAILED`. This is finer-grained than the table
   — strictly more specific, not coarser; `SafeYamlTest` asserts each code independently.
4. **Backend read paths.** DES-008c sketch suggested SQL `jsonb->>` filtering; the
   implementation reads via JPA and filters in-JVM (the datasets are demo-sized).
   Filtering/sort/empty-state semantics are unchanged and asserted (FR-050). No behavior
   change.

No requirement or principle was contradicted by any deviation.

## 5. Known limitations (real ones only)

- **Concurrency model.** Duplicate publication of the same revision is prevented by the
  `(sop_id, draft_revision)` unique constraint → 409. This is correct for the required
  semantics ("duplicate publication of the same revision returns 409"; two concurrent
  publishers of the same saved revision must not both win). The advisory lock still
  serializes same-SOP version assignment. This is by design (PRN-006), not a gap.
- **`GET /sops` and `PublicApi.get` do not filter in SQL** (they do in-JVM). Fine at
  demo scale; would be revisited only under real scale — out of the required scope
  (PRN-002 "implement only the scope explicitly required").
- **Frontend human/JSON view toggle** shares one snapshot object but is a client-side
  mode switch, not a server "freeze". FR-053 is satisfied because both views render the
  *same fetched* `Envelope`; a subsequent publish triggers a fresh fetch (new snapshot),
  not a partial mix.
- **Docker healthcheck for the backend** runs inside the JRE image (wget present); the
  demo has no liveness beyond compose healthchecks, which is what the requirements
  specify.

## 6. Assumptions (material, from ambiguity)

- **Identity source of record** is the `X-Demo-User` request header with values
  `demo-author`/`demo-consumer` (REQUIREMENTS "fixed local identities … sent via
  X-Demo-User"). Chosen to keep the backend authoritative for 401/403.
- **"Revision" semantics** = the draft row's `revision` counter (server-assigned,
  increments on save), used as the publish key and for stale detection (409).
- **`revision:1` on publish with no draft** → `404` (no draft for that `sop_id`), not a
  409; `409 STALE_REVISION` is reserved for a different stored revision; 409
  `PUBLICATION_CONFLICT` for re-publishing an already-published revision.
- **Empty list/filter result** → `200 []` (empty state) rather than 404 (FR-050).
- **`risk` set** is `low`/`medium` and `domain` is `Billing`/`Support` (spec §1–§2);
  `max_autonomy` is `assist` only; unsupported values are validation errors (422/
  issues), per PRN-002.
- **Seed** inserts a saved *draft* (not a publication), so a fresh consumer `GET
  /sops/{id}` is 404 until the author publishes (matches FR-045 "If nothing has been
  published, consumer detail returns 404").
- **Local-only credentials** in `compose/docker-compose.yml` (`sop`/`sop`) are
  explicitly demo placeholders per NFR-001 ("safe local defaults … never in source
  outside compose"); they are not real secrets.
- **Testcontainers vs docker-run for PG.** `AbstractPostgresTest` starts `postgres:16`
  via the docker CLI (in this environment the Testcontainers runtime negotiates a Docker
  API version modern engines reject). Behavior is identical — a real, isolated
  Postgres 16 per test class — and satisfies "use PostgreSQL for database-specific
  behavior" (NFR-041 / PRN-007).

## 7. Delivery status (PRN-009)

Committed and pushed to `ft-opencode-01` (remote `origin/kumarmm0han/harness-benchmark`),
each task bundled with its code/tests/artifacts and task id in the message:
`f14998c` TASK-001…008 · `27ceb8f` TASK-009/010/011 · `879f7b8` TASK-012/013/014 ·
`c67e3f0` TASK-002 · `9da4de7` TASK-015…018 · `feede73` TASK-003/019/020/021 ·
`1fc7b6c` TASK-019 follow-up (nginx `conf.d`). Head `1fc7b6c` confirmed on the remote
via `git ls-remote`. **No failed pushes; every completed task is delivered.**

## 8. Unresolved items

None. All enumerated requirements and the five acceptance journeys pass; all principle
commitments are honored; `make verify` and `make smoke` are green on a clean checkout.
Known limitations above are by-design scope decisions, all within `REQUIREMENTS.md`.
