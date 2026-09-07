# SOP Demo — Technical Design

Translates `ARCHITECTURE.md` into an implementable design. IDs `DES-1xx`… map to `ARC-*`, and each important decision cites the relevant `REQ`/`PRN`/`ARC`.

## Repository layout

```
.
├── ARCHITECTURE.md  TECHNICAL_DESIGN.md  TASKS.md  VERIFICATION.md  spec.md  PRINCIPLES.md  REQUIREMENTS.md
├── Makefile                     # make demo | verify | smoke | clean-data | stop
├── docker-compose.yml           # db, backend, frontend (PRN-003, NFR-001)
├── README.md                    # startup, journeys, shutdown, data removal, API doc (NFR-001, IR-001)
├── backend/
│   ├── Dockerfile               # multi-stage maven build → jre runtime (NFR-001)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/sopdemo/
│       │   ├── SopDemoApplication.java
│       │   ├── config/          # CORS, identity config, openapi-free (PRN-003)
│       │   ├── api/             # controllers + DTO records + @RestControllerAdvice
│       │   ├── content/         # ContentEngine, YamlSafe, SectionParser, validators, FinancialSafety
│       │   ├── persistence/     # DraftStore, PublicationStore, PublicationService
│       │   ├── seed/            # SeedInitializer (demo profile)
│       │   └── identity/        # IdentityFilter + Identity (principal)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/migration/V1__init.sql
│       │   └── seed/duplicate-charge.md
│       └── test/java/com/sopdemo/     # unit tests (content) + @SpringBootTest (persistence, API, identity)
├── frontend/
│   ├── Dockerfile               # node build → nginx static+proxy (NFR-001)
│   ├── nginx.conf
│   ├── package.json  tsconfig.json  vite.config.ts  index.html
│   └── src/
│       ├── main.tsx  App.tsx  api.ts  types.ts  identity.ts
│       ├── screens/ (List, Editor, Detail, HumanView, JsonView)
│       └── __tests__/
└── tests/smoke.sh               # API smoke vs Compose (NFR-041)
```

## Backend — content engine (`content/`)

### DES-101 — Canonical content model is a validated `LinkedHashMap`
Supports: FR-021, PRN-001, PRN-005, ARC-003
`spec.md` §4 requires the canonical content to be the exact mapped object, with no implicit defaults or conversions, preserving array order and all supported values. The simplest lossless representation is to build a `LinkedHashMap<String,Object>` (strings, booleans, finite numbers, lists, maps) from the parse+validate pass. `ContentDocument` is a small record wrapping `Map<String,Object> content` plus `validate→issues`. Jackson serializes the map directly; the published snapshot is the same shape. This avoids a generated POJO tree that would either drop keys or fabricate defaults (forbidden), and keeps order deterministic so `identical source ⇒ structurally identical content` is testable with object equality.

### DES-102 — Single `ContentEngine` entrypoint with pipeline stages
Supports: FR-020/021/030/032/034, PRN-005, ARC-003
`ContentEngine.analyze(String source) → Analysis(valid, issues, content)`. Stages in order:
1. **Size gate** — reject > 65,536 UTF-8 bytes (controlled issue; controller maps to 413).
2. **Parse** — `SectionParser` splits front matter and the 7 sections; `YamlSafe` loads each YAML region.
3. **Structural** — front-matter fields/enums/types; section presence/order/structure; per-structure required fields; unknown-key rejection; duplicate-key/alias/tag rejection; type checks (finite numbers, booleans, strings).
4. **Semantic** — unique input/rule/action IDs; reference integrity (condition inputs exist, action_ids exist); operator/value-type consistency; `≥1 input/rule/action`; refund-specific invariants.
5. **Compile** — build the canonical `Map` (only reached when `issues` empty at the levels that produced it; `valid = issues.isEmpty()`).
Determinism: all collections built from source order; issues are sorted at the end by `(path, code)` so the caller always sees a stable order (PRN-005, FR-034). If structural fails, semantic may be skipped (explicitly allowed by FR-034) — but we still return structural issues.

### DES-103 — Safe YAML loader (`YamlSafe`)
Supports: FR-020, NFR-020, PRN-004, ARC-003
Wrapper over SnakeYAML `SafeConstructor` + `LoaderOptions`:
- `setMaxAliasesForCollections(0)` → aliases rejected.
- Custom tags: use `SafeConstructor` which only constructs core scalar/collection types; any other tag → controlled constructor error (caught → structural issue `unsupported-type`).
- Non-finite numbers: SnakeYAML core schema maps `.nan`/`.inf` to `Double.NaN/Infinity`; we scan all resulting numbers and reject non-finite (`non-finite-number`).
- Nesting depth ≤ 20 (root collection = level 1): after parse, an **iterative** depth check (explicit stack, not recursion) to avoid stack overflow on hostile input → `nesting-too-deep`. We additionally `catch (StackOverflowError)` around the parse and convert to `nesting-too-deep` (defense in depth; hostile YAML could otherwise recurse unboundedly inside SnakeYAML).
- Duplicate keys: SnakeYAML `SafeConstructor` with `LoaderOptions.setAllowDuplicateKeys(false)` → `duplicate-key`.
Each violation maps to a stable structural issue code; `issues` carries `path` so the UI can point to the field (FR-034).

### DES-104 — Section parser rules
Supports: FR-020/021, spec §1, ARC-003
- Source must begin with `---` on line 1, a matching `---` closing the front matter, then exactly the 7 H2 headings **in spec order** (assumption, recorded), each exactly once.
- `Intent (When to use)` / `Do Not Use When`: one or more nonblank lines, each a `- ` bullet → compiled as trimmed strings (bullet marker removed). Any other nonblank line → structural `section-structure`.
- Machine sections (Inputs Required, Eligibility Rules, Actions, Boundaries, Customer Messages): exactly one fenced ` ```yaml ` block and no other nonblank content; the block body is loaded by `YamlSafe`. `Boundaries` must parse to a map, `Customer Messages` to a map, the three lists to lists.
- Any heading not an H2 (or a H2 that isn't one of the 7) → structural `unknown-section`.
- Inline markup/HTML in prose is retained verbatim as text (never interpreted) → satisfies "never execute authored content" (PRN-004).

### DES-105 — Validation codes (stable)
Supports: FR-034, PRN-005
A fixed enum of `code` values, e.g. `missing-field`, `unknown-field`, `duplicate-key`, `unsupported-type`, `non-finite-number`, `invalid-enum`, `invalid-id`, `bad-reference`, `type-mismatch`, `operator-mismatch`, `empty-required-text`, `section-structure`, `unknown-section`, `refund-missing-limit`, `refund-missing-escalation`, `refund-ambiguous`, `nesting-too-deep`, `size-exceeded`, `alias-or-tag`, `sop-id-charset`. Each has a stable `stage` (structural|semantic) and a message template. Centralized in one place (PRN-005).

### DES-106 — Central financial-safety module (`FinancialSafety`)
Supports: FR-032, PRN-005, ARC-003
An independent pure function `checkRefundInvariants(parsedCtx) → List<Issue>` invoked during the semantic stage only when `intent == refund_duplicate_charge`. Centralizes FR-032 so it is testable in isolation:
- require **exactly one** `refund` action and a numeric input named `refund_amount` → `refund-ambiguous`/`refund-missing-limit`;
- require a boundary referencing that refund action + `refund_amount`, `op: gt`, `amount == max_amount`, targeting an existing `escalate` action; a missing limit and a missing escalation are reported **independently** (FR-032) → `refund-missing-limit` vs `refund-missing-escalation`.
It reads only the parsed structures — it never executes a refund (PRN-004). `answer_question` is exempt from the financial requirement but still cannot carry `refund` actions (spec §2) → `refund-not-allowed`.

### DES-107 — `POST /validate` and compile are stateless
Supports: FR-034, ARC-003/002
The validate endpoint and the draft save do not write to any table. A content-invalid save still persists the draft verbatim (allowed) but returns `{sop_id, revision, source}` (IR-001). Validation issues never mutate state.

## Backend — persistence & publication

### DES-201 — Schema (Flyway `V1__init.sql`)
Supports: DR-001, FR-042/043/045, PRN-006, ARC-004/005
```sql
CREATE TABLE drafts (
  sop_id        text PRIMARY KEY,
  source        text NOT NULL,
  revision      bigint NOT NULL,
  publish_failed boolean NOT NULL DEFAULT false,
  saved_at      timestamptz NOT NULL
);
CREATE TABLE sops (          -- single current pointer per SOP
  sop_id        text PRIMARY KEY,
  current_version int NOT NULL
);
CREATE TABLE sop_versions (  -- immutable snapshots
  sop_id        text NOT NULL,
  version       int  NOT NULL,
  source        text NOT NULL,
  canonical     jsonb NOT NULL,
  published_at  timestamptz NOT NULL,
  source_revision bigint NOT NULL,
  PRIMARY KEY (sop_id, version),
  UNIQUE (sop_id, source_revision)
);
```
`PRIMARY KEY (sop_id, version)` enforces unique versions; `UNIQUE (sop_id, source_revision)` enforces unique published draft revisions (FR-042). Snapshots are insert-only (no UPDATE/DELETE paths in application code → FR-043 immutability).

### DES-202 — Draft save (atomic upsert + revision)
Supports: FR-010, FR-045, ARC-004
`INSERT INTO drafts(sop_id, source, revision, publish_failed, saved_at)
   VALUES(?,?, 1, false, now())
ON CONFLICT (sop_id) DO UPDATE
   SET source=excluded.source, revision=drafts.revision+1, publish_failed=false, saved_at=excluded.saved_at
RETURNING sop_id, revision, source;`
The `DO UPDATE` clears the failure indicator on a subsequent save (FR-045). `revision` is server-owned and increments on each save (FR-010).

### DES-203 — Publication transaction (atomic, coordinated)
Supports: FR-042/043/045, PRN-006, ARC-005
A single `@Transactional` method with `isolation = REPEATABLE_READ`:
1. `SELECT ... FROM drafts WHERE sop_id=? FOR UPDATE` → absent ⇒ 404; `revision` mismatch ⇒ 409; capture source + revision + `publish_failed`.
2. Re-run `ContentEngine.analyze(source)`. If invalid ⇒ `UPDATE drafts SET publish_failed=true` (persist failure indicator, FR-045) and return 422 with issues.
3. If `content.sop_id != path sop_id` ⇒ 422 (assumption, recorded).
4. `INSERT ... ON CONFLICT (sop_id) DO NOTHING` into `sops`; then `SELECT current_version FROM sops WHERE sop_id=? FOR UPDATE` → next `version = current_version+1` (row lock serializes concurrent publishers).
5. `INSERT INTO sop_versions (...)`; `UPDATE drafts SET publish_failed=false`.
6. COMMIT, return envelope `{sop_id, version, published_at, content}`.
Failure/duplicate paths: stale ⇒ 409; duplicate revision trips `UNIQUE (sop_id, source_revision)` ⇒ 422/409; concurrent version clash trips `PRIMARY KEY` ⇒ 409. None leave partial rows (single transaction, PRN-006).

### DES-204 — Reads
Supports: FR-050/053/043, ARC-006
- `GET /sops`: `SELECT v.* FROM sops s JOIN sop_versions v ON (v.sop_id=s.sop_id AND v.version=s.current_version)` with optional `domain`/`risk` (validated against the enums; `400` otherwise), `ORDER BY v.sop_id ASC`, returning all matches (FR-050).
- `GET /sops/{sop_id}`: same join scoped to one SOP; `404` if absent (FR-045).
- `GET /sops/{sop_id}/versions/{version}`: direct `sop_versions` lookup (author-only).
- `GET /drafts`, `GET /drafts/{sop_id}`: read `drafts` (author-only), exposing `revision`, `source`, `publish_failed`.
Consumer reads never touch `drafts` (FR-001/053).

### DES-205 — DTO / error model
Supports: IR-001, PRN-005, ARC-002
Request/response are records mirroring the document table. A single `ApiError{code,message,issues}` and a `@RestControllerAdvice` maps exceptions (including `DataAccessException`, `ConstraintViolation`, `StackOverflowError` from parse) to documented statuses; all 5xx paths return a generic message with no internals (IR-001, NFR-020). `issues` is `[]` when not applicable.

### DES-206 — Identity & authz (single filter)
Supports: FR-001, PRN-004, ARC-002
A `OncePerRequestFilter` on `/api/v1/**` reads `X-Demo-User`. Missing/unknown ⇒ 401. It sets a principal with `role = AUTHOR|CONSUMER`. Controllers declare the required role; a consumer hitting an author endpoint ⇒ 403. The two identities are fixed in config (DR-003). This is the only authz surface — no per-controller duplication (PRN-004).

### DES-207 — CORS
Supports: IR-001, NFR-020
CORS limited to the single configured local UI origin (default `http://localhost:3000`, overridable via env `SOP_CORS_ORIGIN`) with the needed methods/headers; everything else disallowed. Requests through the nginx proxy are same-origin and rely on the proxy rather than CORS.

### DES-208 — Deterministic seed
Supports: DR-003, PRN-007, ARC-007
`SeedInitializer` runs (under `demo` profile) after Flyway, using `INSERT INTO drafts(sop_id, source, revision,...) SELECT ... WHERE NOT EXISTS (SELECT 1 FROM drafts WHERE sop_id='...')`, reading the spec.md fixture from `seed/duplicate-charge.md`. Idempotent: restart never duplicates or overwrites an existing draft (DR-003).

## Frontend (DES-3xx)

### DES-301 — App shell & state
Supports: FR-001, NFR-050, ARC-001
A small `useReducer`-based app holds `{ identity, view }`. `identity` is a required gate: the UI refuses API calls until an identity is selected and shows a demo-only selector. `view` switches between `list | editor | detail`. No router library (spec scope). All state is plain TS; the single source of truth for a published SOP on the detail screen is one fetched snapshot object shared by the human and JSON panes (FR-053).

### DES-302 — API client (`api.ts`)
Supports: IR-001, ARC-001/002
A thin typed `fetch` wrapper that injects `X-Demo-User`, normalizes error envelopes, and exposes the 8 operations. No request caching — each screen fetches fresh, so a detail screen always shows the snapshot it loaded (FR-053).

### DES-303 — Editor
Supports: FR-010, FR-034, NFR-050, spec §5
A `<textarea>` bound to `source`. Buttons: *Insert template* (loads the spec.md valid template into the textarea), *Save* (`PUT /drafts/{sop_id}`), *Validate / Preview* (`POST /validate`), *Publish* (`POST /sops/{sop_id}/publish` with the saved revision). A visible badge shows *unsaved changes* and *publication failed (previous version retained)*. Validation issues render grouped by stage (structural|semantic) with the `path`, using text (not color) (FR-034, NFR-050). Publishing requires the current text to equal the saved revision (unsaved edits must be saved first) — enforced in the UI, but the *authoritative* enforcement is the backend's stale 409 (PRN-004). Save shows an explicit error (413/400/500) without discarding the textarea (FR-010). `sop_id` for the path is read from the front matter of the current text when present, else a required field; the backend treats the parsed `sop_id` as authoritative at publish.

### DES-304 — Human & JSON views
Supports: FR-052, FR-053, NFR-050
`DetailView` fetches `GET /sops/{sop_id}` once and passes the same object to both the `HumanView` and `JsonView`. `HumanView` renders identity/title/version, then policy, inputs, rules (source order), actions, boundaries, and customer messages as **text** (never `dangerouslySetInnerHTML`), with a banner making clear that action/message strings describe an SOP and that no real action was executed (FR-052, NFR-020). `JsonView` is a read-only `<pre>` of `JSON.stringify(snapshot, null, 2)`. Both show the `sop_id`+`version` so a concurrent publish cannot mix versions (FR-053).

### DES-305 — Accessibility & discovery
Supports: NFR-050
All actions are buttons (keyboard operable). Status/error messages are visible text with `role="status"`/`role="alert"`, not color-only. No external assets; the app is usable offline in Docker.

## Cross-cutting

### DES-401 — Testing strategy
Supports: NFR-041, PRN-008
- **Unit** (backend `content/`): size gate; hostile YAML (aliases, custom tags, deep nesting, non-finite, duplicates); structural failures (missing sections, unknown fields, bad enums/types); semantic failures (bad references, operator/type mismatch, no inputs/rules/actions); `FinancialSafety` independence (missing limit vs missing escalation vs both; refund not allowed for `answer_question`); determinism (identical source ⇒ equal content + ordered issues).
- **Integration** (backend, real PostgreSQL via Testcontainers): identity 401/403; draft revision + failure-flag clear; atomic publication (valid → v1, invalid → 422 + previous retained, stale → 409, duplicate → 409, concurrent → single winner by constraint); immutable versions (v2 leaves v1 byte-identical); `sop_id` mismatch; 413; human/JSON consistency (the exact same row object returned by both endpoints).
- **Frontend**: component tests that identity gating blocks calls; the detail screen renders identical identity/version/policy in both panes from one object; filter params are sent; error text renders; keyboard-accessible buttons.
- **Smoke** (`tests/smoke.sh`, real Compose): the AC-E2E journeys end-to-end over HTTP, exit nonzero on any failure (NFR-041).

### DES-402 — Build & quality gates
Supports: NFR-001/041
`make verify` = backend `mvn -q test` (unit+integration), frontend `tsc --noEmit` + `npm test` + `npm run lint` (ESLint) + `npm run build`. These are the recorded evidence commands (PRN-008).
