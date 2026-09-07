# SOP Demo — Technical Design

Translates `ARCHITECTURE.md` into an implementable design. Design IDs `DES-0xx` each
reference the `ARC-*`, requirement, and principle it serves. Priorities on conflict:
`PRINCIPLES.md` > `REQUIREMENTS.md` > `ARCHITECTURE.md` > this doc > `TASKS.md`.

---

## 1. Backend — package & module structure

`com.sopdemo` (single module, Spring Boot 3.5, Java 21).

```
com.sopdemo
├─ SopDemoApplication            # @SpringBootApplication
├─ config
│  ├─ WebSecurityConfig          # CORS, HandlerInterceptor registration (DES-010, DES-011)
│  └─ DemoProperties             # @ConfigurationProperties "sopdemo.*" (DES-012)
├─ web
│  ├─ IdentityFilter             # X-Demo-User -> AuthenticatedUser in request (DES-010)
│  ├─ DraftController            # PUT/GET /drafts, GET /drafts/{id} (DES-013)
│  ├─ PolicyAuthorController     # POST /validate, POST /sops/{id}/publish (DES-013)
│  ├─ CatalogController          # GET /sops, /sops/{id}, /sops/{id}/versions/{v} (DES-013)
│  ├─ ApiErrors                  # exception types + @ControllerAdvice (DES-011)
│  └─ dto/                       # request/response records (DES-009)
├─ domain
│  ├─ model/                     # canonical content records (DES-003)
│  ├─ parse/MarkdownDoc, SafeYaml (DES-002)
│  ├─ issue/ValidationIssue, IssueCode (DES-004)
│  └─ service/
│     ├─ ParsingService          # source -> Document (DES-002)
│     ├─ CompilationService      # Document -> canonical Content (DES-003)
│     ├─ ValidationService       # Content/Document -> issues (DES-004/005/006)
│     └─ FinancialSafety         # FR-032 checks (DES-006)
└─ persistence
   ├─ DraftRepository            # JDBC (DES-007)
   ├─ PublicationRepository      # JDBC (DES-007/008)
   ├─ DraftStore, PublicationService (transactional) (DES-008)
   └─ migration/                 # Flyway V1.. (DES-007)
```

Rationale: three-phase pipeline services are plain classes (no framework) so they are
unit-testable in isolation (`PRN-004`, `PRN-005`). Persistence is thin JDBC over a small
number of tables (`ARC-004`, `PRN-003`, `PRN-006`).

## 2. Domain model (canonical content) — `DES-003`

Plain immutable `record`s mapping exactly to `spec.md` §4. `JSON` (Jackson) serializes them.

```
Content { sop_id, title, owner_team, domain, intent, risk_level, max_autonomy,
          Policy{use_when[], do_not_use_when[]},
          InputDecl[]{name,type}, Rule[]{id, Condition[]{input,op,value}, action_ids[]},
          Action[]{id,kind,description,max_amount?},
          Boundaries{ Escalation[]{action_id,input,op,amount,target_action_id}[] },
          CustomerMessages{primary,escalation} }
Published { sop_id, version:int, published_at:Instant(UTC), content:Content }  // spec §4 envelope
```
No optional defaults or implicit conversions (`spec.md` §4). Numbers are finite
doubles; booleans are booleans; strings stay strings.

## 3. Safe YAML + Markdown parsing — `DES-002` (`ARC-003`, `FR-020`, `PRN-004`, `NFR-020`)

`MarkdownDoc.parse(source)` returns either a `Document` or throws `ParseFailure` (→ issue).

Steps, all pure and total w.r.t. the frozen format:
1. **Byte limit:** UTF-8 bytes of `source` ≤ 65,536 else `SOURCE_TOO_LARGE` (413 at API).
2. **Front matter:** first line `---`, collect until next `---`; parse that block as YAML
   → the front-matter mapping. Missing/unclosed → structural error.
3. **Sections:** split the remainder on level-two headings (`## `). Require the exact set
   of seven known headings, each once; any other heading or a missing one → error
   (`UNKNOWN_SECTION`/`MISSING_SECTION`). (Assumption: order not mandated, recorded.)
4. **Prose sections** (`Intent (When to use)`, `Do Not Use When`): only blank lines and
   `- ` bullet lines are allowed; other nonblank lines → `PROSE_STRUCTURE_INVALID`.
   Compile bullets → trimmed strings (markers removed, inline markup kept verbatim).
5. **Machine sections:** the body must be exactly one fenced `yaml` block plus blank lines;
   any other nonblank content or a second fence → error. Extract the block text and run
   `SafeYaml.parse(text, expectedKind)`.

`SafeYaml.parse`:
- **Anchors/aliases:** tokenize with SnakeYAML `Scanner`; any `ANCHOR`/`ALIAS` token →
  `ALIASES_REJECTED` (rejecting at the token level avoids text false-positives).
- **Custom/unknown tags:** use `Constructor.SafeConstructor` (throws on non-core tags) →
  caught as `CUSTOM_TAG_REJECTED`.
- **Duplicate keys:** a `SafeConstructor` subclass overrides mapping construction to detect
  a repeated key within a single mapping → `DUPLICATE_KEY` (with best-effort path).
- **Nesting:** recursive walk of the produced object graph; collection depth > 20
  (root mapping/list = level 1) → `NESTING_TOO_DEEP`.
- **Non-finite numbers:** the graph is walked; any `Double`/`Float` with
  `!isFinite` → `NON_FINITE_NUMBER`.
- **Type fidelity:** scalars keep their parsed type (String/Boolean/Integer/Double); no
  coercion.

All parse-stage failures are `stage=structural` issues with a stable, ordered `path`
(path = `frontmatter` | section name | section-relative `a.b[0]c`).

## 4. Validation — `DES-004/005/006` (`ARC-003`, `FR-030/032/034`, `PRN-005`)

`ValidationService.validate(Document) → Issues` (sorted by `path` then `code`, `PRN-005`).

**Stage A — structural** (always run; shape only, no cross-references):
- front-matter fields present, string-typed, nonempty where required; `sop_id ~
  [A-Z][A-Z0-9-]{0,63}`; `domain ∈ {Billing,Support}`; `intent ∈
  {refund_duplicate_charge, answer_question}`; `risk_level ∈ {low,medium}`;
  `max_autonomy == "assist"`; `intent=refund_duplicate_charge ⇒ domain=Billing`
  (`DOMAIN_FOR_INTENT`).
- per-item field presence/type, unique names/ids, id patterns:
  input `name ~ [a-z][a-z0-9_]{0,63}`; input `type ∈ {number,boolean}`;
  rule/action ids `~ [A-Za-z][A-Za-z0-9_-]{0,63}`; empty text (`EMPTY_TEXT` for required
  `description`/`title`/messages); exactly-one-`refund` rule handled in semantic.
- ≥1 input, ≥1 rule, ≥1 action (`MISSING_*`); no optional invented fields; reject unknown
  keys already caught at parse (`UNKNOWN_KEY`).

**Stage B — semantic** (only if Stage A produced no issues; `FR-034` allows omitting these on
structural failure):
- each condition `input` is a declared input (`UNKNOWN_INPUT_REFERENCE`); each `action_ids`
  entry exists (`UNKNOWN_ACTION_REFERENCE`); operator/value type match the input's declared
  type (`OP_VALUE_TYPE_MISMATCH`); boolean inputs allow `eq` only (`BOOL_OP_RESTRICTION`);
  operators ∈ {eq,gt,lte}.

**Financial safety — `DES-006`** (centralized, independently unit-testable, `PRN-005`):
For `intent=refund_duplicate_charge`: require exactly one refund action with a positive
finite `max_amount`; require a numeric input named `refund_amount`; require ≥1 escalation
boundary with `action_id`=that refund action, `input=refund_amount`, `op=gt`, `amount==`
refund `max_amount`, and `target_action_id` an existing `escalate` action. Missing limit and
missing escalation are emitted as **separate** issues (`FINANCIAL_MISSING_LIMIT`,
`FINANCIAL_MISSING_ESCALATION`), plus `FINANCIAL_AMOUNT_MISMATCH` when present but unequal.
For `intent=answer_question`: **no** refund actions allowed (`ANSWER_QUESTION_REFUND`),
no financial-boundary requirement (avoid relabeling bypass, `spec.md` §2).

## 5. Persistence & publication — `DES-007/008` (`ARC-004/005`, `DR-001`, `PRN-006`)

Flyway `V1__init.sql` on an empty DB (named volume, `DR-001`, `PRN-007`):

```
sop_draft       (sop_id TEXT PK, source TEXT NOT NULL, revision BIGINT NOT NULL,
                 publication_failed BOOLEAN NOT NULL DEFAULT FALSE, updated_at TIMESTAMPTZ)
sop_publication (id BIGSERIAL PK, sop_id TEXT NOT NULL, version INT NOT NULL,
                 draft_revision BIGINT NOT NULL, source TEXT NOT NULL,
                 snapshot JSONB NOT NULL, published_at TIMESTAMPTZ NOT NULL,
                 UNIQUE (sop_id, version), UNIQUE (sop_id, draft_revision))
sop_current     (sop_id TEXT PK, version INT NOT NULL)
```

**Save (DES-008):** `PUT /drafts/{id}` → 64 KiB check → upsert source, `revision++`
(existing or 1), `publication_failed=false` — a single transaction. Allowed pre-valid
(`FR-010`).

**Publish (DES-008)** — one transaction, per-`sop_id` serialized:
1. `SELECT ... FROM sop_draft WHERE sop_id=? FOR UPDATE` (row lock ⇒ concurrency,
   `PRN-006`, "ordinary database coordination").
2. if `req.revision != draft.revision` → rollback, `409` (stale).
3. re-parse/compile/validate the saved `source`.
   - invalid → `UPDATE draft SET publication_failed=TRUE` → commit → `422 {issues}`
     (`FR-045`).
   - valid → next = `COALESCE(MAX(version),0)+1`; `INSERT sop_publication(..., next,
     draft.revision, ...)`; `UPSERT sop_current`; `UPDATE draft SET publication_failed=FALSE`
     → commit → return `Published` envelope. `UNIQUE` constraints are the backstops;
     a constraint violation ⇒ rollback + `409` (duplicate revision).
No partial versions; failed txn = no change. `GET .../versions/{v}`/`GET /sops/{id}` read
the `sop_publication`/`sop_current` rows; missing → `404`.

**Seed (DES-015, `DR-003`):** an `ApplicationRunner` guarded by a `sopdemo.seed.enabled`
flag (on only for the explicit demo profile) does `INSERT ... ON CONFLICT DO NOTHING`
for the `spec.md` example as a **draft** (not published), and never overwrites an existing
draft for that `sop_id`. Idempotent across restarts; preserves user edits.

## 6. Authorization — `DES-010` (`ARC-002`, `FR-001`)

A `HandlerInterceptor` resolves `X-Demo-User`:
- value ∈ {`demo-author`, `demo-consumer`} else `401`;
- endpoint access matrix (author-only: validate, drafts*, validate, publish,
  versions/{v}; either: list, detail). Consumer on an author endpoint → `403`.
Applied uniformly in the backend (not the UI). `WebSecurityConfig` registers it for
`/api/v1/**` only; a `CorsConfiguration` permits exactly the configured local UI origin
(`DES-012`).

## 7. Error/DTO conventions — `DES-009/011` (`IR-001`, `PRN-005`, `NFR-020`)

- `@ControllerAdvice` maps a fixed set of `ApiError` subtypes to statuses:
  `400` malformed envelope/invalid filter, `401/403` identity, `404` absent, `409`
  revision/duplicate, `413` oversized, `422` rejected publish content; unexpected →
  `500` generic. All bodies are `{code,message,issues}` with `issues:[]` when none.
  No SQL/stack/credential/parser internals escape.
- DTO records: `ValidateRequest{name?source}`, `SaveRequest{source}`,
  `PublishRequest{revision}`, list-item / snapshot / draft-summary `record`s.
- Determinism: identical source ⇒ identical `content`/`issues`; only publication metadata
  (`version`, `published_at`) varies (`PRN-005`, `spec.md` §4).

## 8. Frontend (React/TS) — `DES-013/014` (`ARC-001/006`, `NFR-050/020`)

Vite + React 18 + TypeScript. State-based navigation (no router dependency): view ∈
{list, editor, detail} + current identity + filters.

- `src/api.ts` — thin `fetch` client for `/api/v1`, sends `X-Demo-User`, normalizes
  `ApiError` bodies. Single place for requests (keeps logic out of views).
- `src/template.ts` — the exact `spec.md` valid template (for insert).
- Components: `IdentitySelector` (labeled demo-only), `SopList` (+ domain/risk filters,
  author draft-list toggle), `Editor` (textarea + insert-template + save/validate/publish +
  unsaved-changes guard), `HumanView`, `JsonView`.
- **Rendering safety:** all authored strings rendered as text nodes (never
  `dangerouslySetInnerHTML`), satisfying "raw HTML disabled" (`NFR-020`, `FR-052`).
  HumanView + JsonView consume the *same* fetched snapshot object, so identity/version and
  policy match (`FR-053`).
- **Role-aware:** author-only controls (save/validate/publish/draft list) are
  disabled/hidden for `demo-consumer`; status & errors shown as text (not color alone),
  keyboard-usable (`NFR-050`).
- **Unsaved guard:** the editor tracks dirty state; publish is disabled until the current
  buffer is saved; save errors surface without discarding the textarea content (`FR-010`).

Testing: Vitest + `@testing-library/react` + jsdom; `tsc --noEmit`; ESLint; `vite build`.

## 9. Configuration — `DES-012`

`sopdemo.*` (author/consumer ids fixed), `spring.datasource.*`,
`sopdemo.cors.allow-origin`, `sopdemo.seed.enabled`. Frontend: `VITE_API_BASE=/api/v1`.
No real credentials in source (`NFR-020`); local demo only.

## 10. Migrations

Flyway manages schema from empty DB on the named volume; no `data.sql` (seed is a guarded
runner, `DES-015`).

## 11. Test strategy — `DES-016` (`NFR-041`, `PRN-005`)

- **Unit (no DB):** `ParsingService` hostile YAML/HTML/limits/dupes/nesting; `CompilationService`
  shape fidelity; `ValidationService` structural/semantic; `FinancialSafety` missing limit
  vs missing escalation vs mismatch (distinct codes); stable issue ordering; determinism.
- **Integration (PostgreSQL):** identity enforcement (401/403); save revision/restart;
  atomic publish, immutable version, history read, failed indicator, stale(409)/dup(409)/
  invalid-replacement(retains v1), concurrent publish; seed idempotency.
- **Frontend:** component behavior (filters, role gating, template insert, dirty guard,
  human/JSON same-snapshot, HTML-as-text).
- **`make verify`:** backend unit + integration + frontend test + tsc + lint + build.
- **`make smoke`:** bring up Compose, wait healthy, run the primary author→consumer
  journey via `curl`, assert, teardown-safe, nonzero on failure.

## 12. Delivery (Make / Docker) — `DES-017`

- `docker-compose.yml`: `backend` (multi-stage build), `frontend` (node build → nginx,
  `/api` proxy → backend), `db` (postgres:16-alpine, named volume `sopdemo_pgdata`,
  health checks). Services expose host ports 18080/13000/15432 (env-overridable).
- `Makefile`: `demo` (up --build + readiness), `down`, `verify` (test matrix +
  ephemeral PG), `smoke`, `clean` (remove volume — explicit data deletion), `logs`.
  `verify`'s integration step spins an ephemeral `postgres:16-alpine` on a free port, sets
  the test datasource, runs the tagged tests, then removes it.
- README: URLs, identity selection, five acceptance journeys, shutdown, data removal.

## Pattern-selection notes (`DES-*`)

- **Guarded `ApplicationRunner` seeder** (not `data.sql`): needed because DR-003 forbids
  duplicate/overwrite on repeat startup; simplest approach that is idempotent by
  construction. Supports `DR-003`, `NFR-001`.
- **SQL `FOR UPDATE` row lock** (not an app lock / distributed lock): the per-`sop_id`
  serialization the spec asks for with "ordinary database coordination"; simpler and
  correct here. Supports `FR-042`, `PRN-006`.
- **Token-level anchor/alias rejection:** avoids text-scan false positives (e.g. `AT&T`
  in a description); minimal surface, precise. Supports `FR-020`, `NFR-020`.
