---
Artifact: TECHNICAL_DESIGN.md
Stage: 2 (of 6)
Date: 2026-09-05
Status: v1 (reviewed)
---

# SOP Demo — Technical Design

This document translates `ARCHITECTURE.md` into an implementable design. Each section carries a `DES-*` ID, and every material decision references the `ARC-*`, `REQ-*` (FR/IR/DR/NFR), and `PRN-*` items it supports.

## DES-000 — Repository layout

```
.
├── PRINCIPLES.md            # frozen input
├── REQUIREMENTS.md          # frozen input
├── spec.md                  # frozen input
├── ARCHITECTURE.md          # stage 1
├── TECHNICAL_DESIGN.md      # stage 2 (this file)
├── TASKS.md                 # stage 3 + running tracker
├── VERIFICATION.md          # stage 6
├── README.md                # user-facing: URLs, identity, journeys, lifecycle, data deletion
├── API.md                   # normative API reference (IR-001 detail)
├── Makefile                 # verify / demo / smoke / down-clean
├── compose
│   └── docker-compose.yml   # ARC-013, NFR-001
├── backend
│   ├── Dockerfile           # build: maven image; run: temurin JRE image
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java/com/sop/
│       │   │   ├── SopApplication.java
│       │   │   ├── api
│       │   │   │   ├── IdentityFilter.java            # ARC-002
│       │   │   │   ├── GlobalExceptionHandler.java    # FR-034, IR-001 error envelope
│       │   │   │   ├── AuthorApi.java                 # validate + drafts + publish
│       │   │   │   ├── PublicApi.java                 # list / detail / version
│       │   │   │   ├── dto
│       │   │   │   │   ├── IssueDto.java
│       │   │   │   │   ├── ValidateRequest.java
│       │   │   │   │   ├── ValidateResponse.java
│       │   │   │   │   ├── DraftRequest.java
│       │   │   │   │   ├── DraftDto.java
│       │   │   │   │   ├── DraftsDto.java
│       │   │   │   │   ├── PublishRequest.java
│       │   │   │   │   ├── PublishResponse.java
│       │   │   │   │   ├── SopSummaryDto.java
│       │   │   │   │   ├── SopsDto.java
│       │   │   │   │   ├── PublishedEnvelope.java
│       │   │   │   │   └── ErrorBody.java
│       │   │   ├── domain
│       │   │   │   ├── Draft.java                     # ARC-007
│       │   │   │   ├── Publication.java               # ARC-008
│       │   │   │   ├── SopCurrent.java                # ARC-008 (pointer)
│       │   │   │   ├── DraftRepository.java
│       │   │   │   ├── PublicationRepository.java
│       │   │   │   ├── SopCurrentRepository.java
│       │   │   │   ├── Content.java                   # canonical content model (ARC-006)
│       │   │   │   ├── Issue.java                     # ARC-003
│       │   │   │   ├── ParsedDocument.java            # ARC-003
│       │   │   │   ├── pipeline
│       │   │   │   │   ├── ValidatorPipeline.java     # entry point (ARC-003)
│       │   │   │   │   ├── SafeYaml.java              # safe parser + alias/depth/finite checks
│       │   │   │   │   ├── FrontMatterValidator.java  # front-matter keys, enums, types
│       │   │   │   │   ├── SectionParser.java         # splits markdown into sections
│       │   │   │   │   ├── SectionValidator.java      # required sections, section shape
│       │   │   │   │   ├── TypeValidator.java         # supported types per field
│       │   │   │   │   ├── ReferenceValidator.java    # input/ref/action/id consistency
│       │   │   │   │   ├── FinancialValidator.java    # FR-032 dedicated
│       │   │   │   │   └── CanonicalBuilder.java      # ARC-006
│       │   │   ├── service
│       │   │   │   ├── DraftService.java              # ARC-007
│       │   │   │   ├── PublishService.java            # ARC-008
│       │   │   │   ├── ReadService.java               # ARC-009 / ARC-010
│       │   │   │   └── ValidationService.java         # thin wrapper over pipeline
│       │   │   ├── config
│       │   │   │   ├── AppProperties.java
│       │   │   │   ├── CorsConfig.java                # ARC-015, NFR-020
│   │   │   │   ├── IdentityConfig.java            # ARC-002 (fixed identities)
│       │   │   ├── seed
│       │   │   │   └── SeedInitializer.java           # ARC-011, DR-003
│       │   │   └── migration
│       │   │       └── V1__schema.sql                 # ARC-012
│       │   └── resources
│       │       ├── application.yml
│       │       └── db/migration/V1__schema.sql
│       └── test
│           ├── java/... (mirrors)
│           └── resources
└── frontend
    ├── Dockerfile           # node image builds; nginx image serves
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── nginx.conf
    ├── index.html
    ├── tests/
    │   ├── setup.ts
    │   └── *.test.tsx
    └── src
        ├── main.tsx
        ├── App.tsx
        ├── api
        │   ├── client.ts
        │   └── types.ts
        ├── state
        │   └── IdentityContext.tsx
        ├── components
        │   ├── IdentitySelector.tsx
        │   ├── SopList.tsx
        │   ├── FilterBar.tsx
        │   ├── DraftList.tsx
        │   ├── Editor.tsx
        │   ├── ValidationPanel.tsx
        │   ├── HumanView.tsx
        │   ├── JsonView.tsx
        │   └── Toast.tsx
        └── styles.css
```

Rationale: flat and predictable. No monorepo tooling beyond what Spring Boot + Vite give us. All business rules in `backend/src/main/java/com/sop/domain/pipeline/*` — one package, one concern each. `service/` is a thin orchestration layer over JPA repos. `api/` is controllers + DTOs + error handler + identity filter. `seed` and `migration` separated by intent.

Supports: ARC-001..ARC-015, PRN-002, PRN-004, PRN-009.

## DES-001 — Constants and issue codes

All constants live in one file `com.sop.domain.Contract.java` so that validators, the builder, tests, and the frontend contract do not diverge.

| Name | Value | Supports |
|------|-------|----------|
| `SOURCE_BYTES_MAX` | `65_536` (64 KiB) | FR-020 |
| `YAML_MAX_DEPTH` | `20` (root mapping/list = level 1) | FR-020 |
| `FRONT_MATTER_KEYS` | `{sop_id, title, owner_team, domain, intent, risk_level, max_autonomy}` | spec §1 |
| `DOMAIN_VALUES` | `{Billing, Support}` | spec §1 |
| `INTENT_VALUES` | `{refund_duplicate_charge, answer_question}` | spec §1 |
| `RISK_VALUES` | `{low, medium}` | spec §1, FR-020 |
| `AUTONOMY_VALUES` | `{assist}` | spec §1, FR-020 |
| `SOP_ID_RE` | `[A-Z][A-Z0-9-]{0,63}` | spec §1 |
| `IDENTIFIER_RE` (input/rule/action) | input `[a-z][a-z0-9_]{0,63}`, rule/action `[A-Za-z][A-Za-z0-9_-]{0,63}` | spec §2 |
| `SECTION_ORDER` | `[Intent (When to use), Do Not Use When, Inputs Required, Eligibility Rules, Actions, Boundaries, Customer Messages]` | spec §1 |
| `SECTION_KIND` | intent/donotuse → bullets; inputs/rules/actions → list; boundaries → object; messages → object | spec §1 |
| `CONDITION_OPS` | `{eq, gt, lte}` | spec §2 |
| `ACTION_KINDS` | `{refund, escalate, human_assist}` | spec §2 |
| `INPUT_TYPES` | `{number, boolean}` | spec §2 |

### Issue codes (stable, sorted by (path, code) before returning)

| code | stage | Trigger |
|------|-------|---------|
| `PARSE_FAILED` | structural | malformed YAML, parse exception, unknown tag/alias |
| `SOURCE_TOO_LARGE` | structural | source > 64 KiB |
| `YAML_NESTING_TOO_DEEP` | structural | any YAML nesting > 20 levels |
| `YAML_NON_FINITE_NUMBER` | structural | `.inf`, `.nan`, `BigInteger` overflow to non-finite double |
| `FRONTMATTER_MISSING` | structural | no/front-matter absent |
| `FRONTMATTER_UNKNOWN_KEY` | structural | unknown front-matter key |
| `FRONTMATTER_TYPE` | structural | front-matter value wrong type (not string) |
| `FRONTMATTER_ENUM` | structural | front-matter enum value not in allowed set |
| `FRONTMATTER_REFUND_DOMAIN` | semantic | `intent: refund_duplicate_charge` with `domain != Billing` |
| `FRONTMATTER_SOP_ID_FORMAT` | structural | `sop_id` does not match `SOP_ID_RE` |
| `SECTION_MISSING` | structural | one of `SECTION_ORDER` absent |
| `SECTION_DUPLICATED` | structural | a section heading appears more than once |
| `SECTION_UNEXPECTED` | structural | heading not in `SECTION_ORDER` |
| `SECTION_SHAPE` | structural | prose section is not bullets / machine section content not a single yaml block |
| `FIELD_UNKNOWN` | structural | unknown field at any object level |
| `FIELD_MISSING` | structural | required field missing |
| `FIELD_TYPE` | structural | value wrong type for the field |
| `FIELD_VALUE` | structural/semantic | enum/op/kind value not supported |
| `FIELD_EMPTY` | structural | required text is empty |
| `DUPLICATE_INPUT_NAME` | structural | two inputs share `name` |
| `DUPLICATE_RULE_ID` | structural | two rules share `id` |
| `DUPLICATE_ACTION_ID` | structural | two actions share `id` |
| `REF_INPUT_UNKNOWN` | semantic | condition `input` not a declared input |
| `REF_ACTION_UNKNOWN` | semantic | rule `action_ids` references an unknown action |
| `REF_CONDITION_OP` | semantic | op not in `CONDITION_OPS` (or boolean input with non-eq op) |
| `REF_CONDITION_TYPE` | semantic | `value` type does not match input type |
| `EMPTY_INPUTS` | semantic | no inputs declared |
| `EMPTY_RULES` | semantic | no rules declared |
| `EMPTY_ACTIONS` | semantic | no actions declared |
| `FIN_REFUND_MISSING` | semantic | no `refund` action for `refund_duplicate_charge` |
| `FIN_REFUND_MAX_AMOUNT` | semantic | refund action missing/invalid `max_amount` |
| `FIN_REFUND_AMOUNT_INPUT` | semantic | `refund_amount` input missing or not `number` |
| `FIN_ESCALATION_MISSING` | semantic | no escalation boundary naming the refund action |
| `FIN_ESCALATION_BOUND` | semantic | escalation boundary present but wrong op/amount/input/target |

`stage` value: `structural` or `semantic`. Issue shape: `{code, stage, message, path}` (FR-034). `path` examples: `front_matter.domain`, `section.inputs[2].name`, `rules[0].conditions[1].op`, `boundaries.escalation[0].amount`, `intent`.

Response envelope (every endpoint, success or failure):

| Body | Meaning |
|------|---------|
| `{code, message, issues}` | unified; `issues: []` when not applicable |
| `{valid, issues, content}` | only for `POST /validate` — content is `null` when `valid` is false |
| `{sop_id, revision, source}` | `PUT /drafts/{id}` and `GET /drafts/{id}` |
| `{sop_id, version, published_at, content}` | `POST /sops/{id}/publish`, `GET /sops/{id}`, and `GET /sops/{id}/versions/{v}` |
| `[{...summary...}, ...]` | `GET /sops` and `GET /drafts` |

`code` on errors: `MALFORMED`, `MISSING_IDENTITY`, `FORBIDDEN`, `NOT_FOUND`, `SOURCE_TOO_LARGE`, `STALE_REVISION`, `PUBLICATION_CONFLICT`, `VALIDATION_FAILED`, `INVALID_FILTER`. All messages are short, human-readable; no SQL, no stack traces, no parser internals (NFR-020).

Supports: FR-020, FR-030, FR-032, FR-034, IR-001, NFR-020, PRN-005.

## DES-002 — Database schema (Flyway `V1__schema.sql`)

```sql
CREATE EXTENSION IF NOT EXISTS pg_catalog."plpgsql";

CREATE TABLE drafts (
  sop_id              text        PRIMARY KEY,
  source              text        NOT NULL,
  revision            bigint      NOT NULL CHECK (revision >= 1),
  publish_failed_at   timestamptz,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE publications (
  sop_id          text           NOT NULL,
  version         integer        NOT NULL CHECK (version >= 1),
  draft_revision  bigint         NOT NULL,   -- revision of the draft that was published
  source          text           NOT NULL,   -- raw source at publication time
  content_json    jsonb          NOT NULL,   -- canonical content (deterministic)
  envelope_json   jsonb          NOT NULL,   -- {sop_id, version, published_at, content}
  published_at    timestamptz    NOT NULL,
  PRIMARY KEY (sop_id, version),
  CONSTRAINT uq_publications_draft_revision UNIQUE (sop_id, draft_revision)
);

CREATE TABLE sop_current (
  sop_id          text        PRIMARY KEY,
  version         integer     NOT NULL,
  published_at    timestamptz NOT NULL,
  CONSTRAINT fk_sop_current_publication FOREIGN KEY (sop_id, version)
    REFERENCES publications(sop_id, version) ON DELETE CASCADE
);
```

Constraint set:
- `publications (sop_id, version)` unique → no duplicate versions.
- `publications (sop_id, draft_revision)` unique → a draft revision can be published at most once; duplicate publish → 409.
- `publications (sop_id, version)` is the PK → atomic upsert for the current pointer.
- `sop_current (sop_id)` is the single pointer → only one row per SOP.
- `ON DELETE CASCADE` from publications → sop_current is auto-cleaned (defensive).
- No `drafts.id` surrogate key — `sop_id` is unique by requirement (one editable draft per SOP).

Concurrent publish strategy: inside the publish transaction (per SOP), take one Postgres advisory lock keyed by `sop_id` — `SELECT pg_advisory_xact_lock(hashtext(:sop_id))`. Inside the lock: recompute next version from `publications`; insert; upsert `sop_current`. Any other concurrent transaction for the same SOP blocks. Different SOPs are never blocked. This is "ordinary database coordination" (PRN-006). A failed publish never writes: either it commits both `publications` and `sop_current`, or it commits neither.

The `drafts.publish_failed_at` column is the failure indicator (FR-045). It is set on a failed publish and cleared on the next save or publish.

Supports: ARC-007, ARC-008, ARC-010, ARC-012, DR-001, PRN-006, PRN-005.

## DES-003 — Safe-YAML strategy (FR-020, NFR-020)

SnakeYAML 2.x removed `AliasNode` and collapsed scalar aliases into plain nodes, so alias rejection cannot rely on the node graph. Strategy:

1. **Token-scan for aliases** — before loading, feed the source through `org.yaml.snakeyaml.scanner.ScannerImpl` and check `scanner.checkToken(Token.ID.Alias)`. This reliably detects all aliases (scalar, collection, flow, or block) and does not fire for `*` inside YAML strings. `*` inside strings is a scalar and never produces an `AliasToken`.
2. **Compose** with `SafeConstructor` + `LoaderOptions`:
   - `setAllowDuplicateKeys(false)` — duplicate keys throw during compose.
   - `setMaxAliasesForCollections(0)` — collection aliases throw during compose (belt-and-suspenders).
3. **Post-walk** the composed `java.util.Collection`/`Map` values in Java:
   - **Nesting depth** — spec: "root mapping/list as level 1", limit 20. Implementation: depth(v) = 0 if v is a scalar, else 1 + max(depth(child) for children). Reject if depth(v) > 20 on any parsed value (front-matter or a section block). So a top-level mapping containing a list of maps is depth 3.
   - **Finite numbers** — reject `Double` values that are `NaN` or infinite (SnakeYAML parses `.inf`, `.nan`, `1e400` to `Double.NaN`/`Infinity`). `BigInteger`/`Long`/`Integer` are whole integers and are finite; legal. Note: a value that should be an integer (e.g. an input `value`, an action `max_amount`, a boundary `amount`) that arrives as a `Double` is still a number — accepted by the type check but must be finite.
   - No value may be a non-finite `Double`; `NaN` and `Infinity` are the only non-finite cases produced by SnakeYAML for the scalar tags the parser accepts.
4. **Custom tags** — `SafeConstructor` rejects unknown tags at compose time; we do not add any `TagInspector`/custom tag constructors, so `!!` tags (other than the plain scalar/mapping/sequence tags) are rejected.
5. **No recursion** — our code never calls `eval`, `exec`, or any reflection into author-supplied class names. `SafeConstructor` only builds Java `String`, `Integer`, `Long`, `Double`, `Boolean`, `LinkedHashMap`, `ArrayList`, `java.util.Date` (we never use it because the format has no date fields).

All exceptions are mapped to issue codes in DES-001 with the source path the validator can compute.

Supports: ARC-003, FR-020, NFR-020, PRN-004, PRN-005.

## DES-004 — Markdown → (front-matter, sections) split

`SectionParser.split(source)` returns `ParsedDocument { frontMatterText, sections: Map<String,String>, sectionOrder: List<String>, issues: List<Issue> }`.

Rules (all produce `structural` issues; parsing continues where possible so all safely-discoverable issues surface — FR-034):
1. The document must begin with `---\n` on line 1. The front matter is the text up to the next line that is exactly `---` (trimmed). If either delimiter is missing → `FRONTMATTER_MISSING`.
2. After the front matter, the body is split on level-two headings. A level-two heading is a line matching `^## (.+)$` (no leading spaces, exactly two `#`). `#` and `###`+ are NOT section headings.
3. A heading line that matches `^# ` or `^#{3,}` anywhere in the body → `SECTION_UNEXPECTED` (a heading not allowed by the format). Any other line before the first `## ` is a `SECTION_UNEXPECTED` (content before first section is not allowed).
4. Each recognized section heading (per `SECTION_ORDER`) is recorded. A heading that matches a recognized name but appears again → `SECTION_DUPLICATED`. A `##` heading not in `SECTION_ORDER` → `SECTION_UNEXPECTED`. A missing one → `SECTION_MISSING`.
5. Section bodies are the lines between this heading and the next `##` heading, preserving internal blank lines. Leading/trailing blank lines are trimmed.

Prose sections (`Intent (When to use)`, `Do Not Use When`): the body must consist only of bullet lines (`- ` prefix, trimmed) and blank lines. Any non-bullet, non-blank line → `SECTION_SHAPE`. Bullets compile to `{text}` with the leading `- ` removed and surrounding whitespace trimmed (spec §4).

Machine sections (`Inputs Required`, `Eligibility Rules`, `Actions`, `Boundaries`, `Customer Messages`): the body must contain **exactly one** fenced `yaml` block. A block is a line ```` ```yaml ```` … up to the closing ```` ``` ````; between them is the YAML. Any other nonblank content outside that single block → `SECTION_SHAPE`. Zero blocks or more than one block → `SECTION_SHAPE`. The block content string is passed to `SafeYaml.load`.

Supports: ARC-003, FR-020, FR-021, PRN-005.

## DES-005 — Pipeline and validators

`ValidatorPipeline.validate(String source)` is the single entry point, deterministic and side-effect free. It returns `PipelineResult { valid, issues, parsed, content }` where `content` is the canonical object (or `null`).

Order (each stage feeds the next; a hard structural parse failure may allow later stages that do not require the missing data to be skipped, per FR-034):

1. `SizeCheck` — UTF-8 byte length ≤ 65,536, else `SOURCE_TOO_LARGE` (short-circuit; no further parsing). `MALFORMED` if not valid UTF-8.
2. `SectionParser` — DES-004. If no front-matter or a fatal structure issue is found, record issues and stop (semantic checks need data).
3. `SafeYaml.load` on the front-matter text and each machine-section block (DES-003). Any alias/depth/finite/tag/dup/parse failure is a `structural` issue with the best-known path.
4. `FrontMatterValidator` — exact key set (no unknown, no missing), all values strings, enums, `sop_id` regex, and rule: `refund_duplicate_charge` ⇒ `domain == Billing` (else `FRONTMATTER_REFUND_DOMAIN`, semantic).
5. `SectionValidator` — each machine section loaded into the right shape: inputs/rules/actions → `ArrayList`, boundaries → `LinkedHashMap` with `escalation`, messages → `LinkedHashMap` with `primary` and `escalation`. Enforce no unknown keys at **every** object level (spec: "Reject unknown keys at every object level") → `FIELD_UNKNOWN`.
6. `TypeValidator` — per-field type checks: input `name`(string)+`type`(enum number|boolean); rule `id`(string)+`conditions`(list)+`action_ids`(list); condition `input`(string)+`op`(enum)+`value`(typed); action `id`(string)+`kind`(enum)+`description`(string)+`max_amount`(number, only for refund); boundary escalation entries fields; messages `primary`/`escalation` nonempty strings. Type mismatch → `FIELD_TYPE`; empty required text → `FIELD_EMPTY`; enum/op/kind wrong → `FIELD_VALUE`.
7. `ReferenceValidator` — unique input names, rule ids, action ids; every condition `input` exists; every `action_ids` entry exists; condition `op` and `value` type match the input's declared type (booleans only `eq`); at least one input, one rule, one action; IDs match regex; booleans support `eq` only.
8. `FinancialValidator` (FR-032) — only when `intent == refund_duplicate_charge`: exactly one `refund` action; that action has positive finite `max_amount`; an input `refund_amount` of type `number`; at least one escalation boundary with `action_id` = that refund action, `input` = `refund_amount`, `op` = `gt`, `amount` == `max_amount` (numeric equality), `target_action_id` = an existing `escalate` action. Missing-limit and missing-escalation are reported as **separate** issues (`FIN_REFUND_MAX_AMOUNT` / `FIN_ESCALATION_MISSING`). `answer_question` must have **no** refund action (else a semantic violation — prevents relabeling to bypass); escalation list may be empty.

All issues are deduplicated and sorted by `(path, code)` for a stable, ordered, deterministic result (PRN-005).

`CanonicalBuilder.build(parsed)` produces the canonical content object (DES-006). It runs only when structural+semantic checks pass; otherwise `content` is `null`.

Supports: ARC-003..ARC-006, FR-020..FR-034, PRN-004, PRN-005.

## DES-006 — Canonical content and envelope

`Content` fields (mirrors spec §4 exactly; no invented data, no defaults):
- `sop_id, title, owner_team, domain, intent, risk_level, max_autonomy` — front-matter strings unchanged.
- `policy`: `{use_when:[...], do_not_use_when:[...]}` from the two prose sections, bullets de-marked and trimmed.
- `inputs`: the Inputs Required list (order preserved).
- `rules`: the Eligibility Rules list (order preserved).
- `actions`: the Actions list (order preserved).
- `boundaries`: the Boundaries object.
- `customer_messages`: the Customer Messages object.

Serialization: Jackson `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled **on input only** (we never serialize unknown fields — canonical is built explicitly), `WRITE_BIGDECIMAL_AS_PLAIN` enabled, and no Java-time auto-module beyond ISO-8601 UTC. Numbers: preserve `Integer`/`Long`/`Double` as JSON numbers; a double with an integral value (e.g. `200.0`) serializes as `200.0` (we do not coerce to int, per "no implicit conversions").

Publication envelope (server-owned; spec §4):
```json
{ "sop_id": "<same as content.sop_id>", "version": <int>, "published_at": "<UTC ISO-8601>", "content": { ... } }
```
The envelope `sop_id` is validated to equal `content.sop_id` before insert. `published_at` = transaction timestamp (UTC). The envelope is stored in `publications.envelope_json` and `content` in `publications.content_json` (and the raw `source`). `content` is the single canonical object shared by human and JSON views (PRN-001).

Supports: ARC-006, FR-021, FR-043, PRN-001, PRN-005.

## DES-007 — Identity and authorization (FR-001, IR-001, NFR-020)

Two fixed local identities are configured in `IdentityConfig` (a `@ConfigurationProperties` / `@Value` block):

| Property | Default | Meaning |
|----------|---------|---------|
| `app.demo.author` | `demo-author` | the author identity |
| `app.demo.consumer` | `demo-consumer` | the consumer identity |
| `app.cors.allowed-origin` | `http://localhost:8075` | UI origin |

`IdentityFilter` (a `OncePerRequestFilter` registered via `FilterRegistrationBean` at `HIGHEST_PRECEDENCE`) runs on every request under `/api/v1/**`:
1. Read `X-Demo-User` (exact header name).
2. If absent or blank → 401 `MISSING_IDENTITY`, short-circuit (response already committed; no controller runs).
3. If value ∈ {author, consumer} → set `request.setAttribute("identity", value)` and continue.
4. If value is any other string → 401 `MISSING_IDENTITY` (unknown identity is indistinguishable from missing to the client for safety; this is a "local-only" demo, not an open id space).

Controllers declare their role via a `@RequestParam`-free convention: each endpoint handler has an expected role (`AUTHOR` or `EITHER`). A tiny annotation-free helper `Identity.authority(request)` returns the role string; controllers call `Role.require(identity, AUTHOR)` at the top, which throws `ForbiddenException` → 403. This keeps the check in exactly one place, server-side (PRN-004 "UI controls cannot replace backend enforcement").

Endpoint → role table (IR-001):

| Endpoint | Role |
|----------|------|
| `POST /api/v1/validate` | AUTHOR |
| `PUT /api/v1/drafts/{sop_id}` | AUTHOR |
| `GET  /api/v1/drafts` | AUTHOR |
| `GET  /api/v1/drafts/{sop_id}` | AUTHOR |
| `POST /api/v1/sops/{sop_id}/publish` | AUTHOR |
| `GET  /api/v1/sops` | EITHER |
| `GET  /api/v1/sops/{sop_id}` | EITHER |
| `GET  /api/v1/sops/{sop_id}/versions/{version}` | AUTHOR |

Supports: ARC-002, FR-001, IR-001, NFR-020, PRN-004.

## DES-008 — Service layer (thin)

### DES-008a — DraftService (ARC-007)
- `save(sopId, source)`: `@Transactional`. Enforce 64 KiB (else `413` → caller catches `SourceTooLargeException`). Upsert `drafts` by `sop_id`. On update: `revision += 1`, clear `publish_failed_at`, `updated_at = now()`, return `(sopId, revision, source)`. On insert: `revision = 1`.
- `get(sopId)`: 404 if absent. Return `{sop_id, revision, source, publish_failed_at}`.
- `list()`: 404 N/A — return a list (may be empty); include each draft's `sop_id`, `revision`, and publish-failure indicator.

### DES-008b — PublishService (ARC-008)
`publish(sopId, requestedRevision)` `@Transactional`:
1. Fetch `drafts(sopId)` — 404 if absent.
2. If `draft.revision != requestedRevision` → 409 `STALE_REVISION`.
3. Run `ValidatorPipeline.validate(draft.source)` (re-validate the *saved* source, not a client-supplied one). If `!valid` → set `drat.publish_failed_at = now()` and persist (a tiny write on an otherwise "read" path), then throw `ValidationFailedException(issues)` → 422.
4. Take advisory lock `pg_advisory_xact_lock(hashtext(sopId))`.
5. `nextVersion = COALESCE((SELECT MAX(version) FROM publications WHERE sop_id), 0) + 1` (read under the lock).
6. Build envelope: `{sop_id, version: nextVersion, published_at: now(), content}`. Verify envelope `sop_id == content.sop_id`.
7. Insert into `publications (sop_id, version, draft_revision, source, content_json, envelope_json, published_at)`.
   - If `draft_revision` already exists → the `UNIQUE` constraint fires → translate `DataIntegrityViolationException` to 409 `PUBLICATION_CONFLICT`.
8. Upsert `sop_current (sop_id, version, published_at)` to point at the new version.
9. Clear `drafts.publish_failed_at` (a successful publish clears the indicator per FR-045).
10. Return the stored envelope (read back from `publications` so the client sees exactly what was stored — PRN-001).

Atomicity: a single transaction wraps steps 2–10. Any exception rolls back all writes. Advisory lock serializes concurrent publishers of the same SOP; the unique `(sop_id, draft_revision)` constraint is the backstop.

### DES-008c — ReadService (ARC-009 / ARC-010)
- `list(domain, risk)`: `SELECT` from `sop_current` joined with `publications` filtered by `domain`/`risk` (the columns are stored inside `content_json`; to keep the WHERE clause in SQL, promote `content.jsonb->>'…'`: query via `envelope_json->'content'->>'domain'`). Filter: each present filter is AND-ed. Sort by `sop_id` ascending. Return `sop_id, title, version, domain, risk`. If no filter is present, return all.
- `get(sopId)`: `SELECT` `envelope_json` from `publications` where `sop_id = ?` and `version = (SELECT version FROM sop_current WHERE sop_id = ?)` — 404 if neither exists. Return the envelope (never source).
- `getHistorical(sopId, version)`: `SELECT envelope_json` from `publications` by PK `(sop_id, version)` — 404 if absent. Author-only.

All read paths are SELECT-only; they never write and never mutate (FR-053: "no consumer request mutates content or executes rules/actions").

Supports: ARC-007, ARC-008, ARC-009, ARC-010, FR-010, FR-042, FR-043, FR-045, FR-050, PRN-006.

## DES-009 — REST controllers and DTOs (IR-001)

All DTOs are immutable records (Java 21 records), serialized by Jackson. All request DTOs are validated for non-null required fields in the controller before calling the service; null/missing → 400 `MALFORMED`.

### Validate
`POST /api/v1/validate` body `{source}`.
- 400 if `source` is null or not a string.
- Call `ValidationService.validate(source)`.
- Response 200: `{valid: bool, issues: IssueDto[], content: Content | null}`.
  - If `valid`: `content` is the canonical object; `issues` is `[]`.
  - If `!valid`: `content` is `null`; `issues` is the full list.
- This is the one endpoint that returns `200` for content issues (per IR-001) — the UI distinguishes the two stages by `Issue.stage`.

### Drafts
- `PUT /api/v1/drafts/{sop_id}` body `{source}` → 200 `{sop_id, revision, source}`. Error: 413 `SOURCE_TOO_LARGE`.
- `GET  /api/v1/drafts` → 200 `[{sop_id, revision, publish_failed: bool}]`.
- `GET  /api/v1/drafts/{sop_id}` → 200 `{sop_id, revision, source, publish_failed: bool}`. 404 if absent.

### Publish
`POST /api/v1/sops/{sop_id}/publish` body `{revision}` → 200 full envelope. Errors:
- 400 `MALFORMED` if `revision` null/missing.
- 404 `NOT_FOUND` if no draft.
- 409 `STALE_REVISION`.
- 409 `PUBLICATION_CONFLICT`.
- 422 `VALIDATION_FAILED` (with full `issues`) if the saved source is invalid.

### Public list/detail
`GET /api/v1/sops?domain=Billing&risk=medium` → 200 `[{sop_id, title, version, domain, risk}]`.
- 400 `INVALID_FILTER` if `domain` is not in `{Billing, Support}` or `risk` is not in `{low, medium}`.
- Empty results → 200 with `[]` (not an error; UI shows empty state).

`GET /api/v1/sops/{sop_id}` → 200 envelope (see DES-006). 404 if no current publication.

`GET /api/v1/sops/{sop_id}/versions/{version}` → 200 envelope (historical). 404 if absent. Author-only.

### Error envelope (all errors)
```json
{ "code": "UPPER_SNAKE", "message": "short, human-readable", "issues": [] }
```
- `issues` is always a list (empty `[]` when inapplicable).
- `5xx` returns `{code: "INTERNAL", message: "An unexpected error occurred.", issues: []}` — no stack trace, no SQL (NFR-020 / PRN-004).

### 400 vs 422 boundary
- 400 is for **envelope / request-shape / filter** violations — nothing the validator could have said about the content.
- 422 is for **content** validation violations on a publish — the validator ran and found structural/semantic issues.
- `POST /validate` returns 200 even with content issues because IR-001 explicitly allows that; the issues are returned in the `issues` array of the body.

Supports: IR-001, FR-010, FR-034, FR-042, FR-050, PRN-004, PRN-005.

## DES-010 — Seed initializer (DR-003, PRN-007)

`SeedInitializer` is a `@Component` gated by `@Profile("demo")` (the profile set in `application.yml` / Compose env). It implements `ApplicationRunner` and runs **after** Flyway migrations. Idempotency rule:

```
if (SELECT COUNT(*) FROM drafts) == 0:
    INSERT INTO drafts(sop_id, source, revision) VALUES ('BILL-REFUND-001', <seed>, 1)
else:
    no-op
```

The seed source is the verbatim valid template from `spec.md §3` (the `BILL-REFUND-001` duplicate-charge document). It is a **saved draft** (not a publication), per DR-003. Fictional data only. Because the guard is "table empty", repeated startup, restart, and re-seed never duplicate the row or overwrite user edits (FR-010 / AC-E2E-005: "usable seed draft" and "retained drafts and publications"). No `sop_current` row is created by seed — consumers see a 404 until the author publishes (FR-045: "If nothing has been published, consumer detail returns 404").

A `SeedTemplate` constant holds the exact seed string; the test suite asserts the seed source is byte-identical to `spec.md §3` and that a second invocation does not change the row.

Supports: ARC-011, DR-003, NFR-001, PRN-007, PRN-002 (no extra seed data invented).

## DES-011 — Configuration, CORS, and error handling (NFR-020)

`AppProperties` bound from `application.yml`:
- `app.demo.author=demo-author`, `app.demo.consumer=demo-consumer` (fixed identities — DR-003).
- `app.cors.allowed-origin=http://localhost:8075`.
- `app.source.max-bytes=65536`.
- `spring.datasource.url=jdbc:postgresql://db:5432/sop` (user/pass from env, not in source).
- `spring.flyway.enabled=true`.

**CORS** (`CorsConfig`): one `CorsConfiguration` for path `/api/v1/**`: allowedOrigin `app.cors.allowed-origin`, allowedMethods = the 8 operations, allowedHeaders = `Content-Type,X-Demo-User`, allowCredentials `false`, maxAge default. No `*` with credentials. `ExposedHeaders` none needed. (ARC-015.)

**Security**: no `spring-boot-starter-security` — the demo identity is a header the backend enforces itself (DES-007); adding the Spring Security dependency is unnecessary for this scope (PRN-003: avoid speculative layers). Actuator is present only for `/actuator/health` used by Compose healthchecks and smoke (path `/actuator/health`, `management.endpoints.web.exposure.include=health`).

**Error handling** (`GlobalExceptionHandler`, `@RestControllerAdvice`): maps the exception types below to the exact IR-001 status codes and the DES-009 error envelope:

| Exception | HTTP | code |
|-----------|------|------|
| `SourceTooLargeException` | 413 | `SOURCE_TOO_LARGE` |
| `MalformedRequestException` | 400 | `MALFORMED` |
| `InvalidFilterException` | 400 | `INVALID_FILTER` |
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `StaleRevisionException` | 409 | `STALE_REVISION` |
| `PublicationConflictException` | 409 | `PUBLICATION_CONFLICT` |
| `ValidationFailedException` | 422 | `VALIDATION_FAILED` (with `issues`) |
| `DataIntegrityViolationException` (unique constraint) | 409 | `PUBLICATION_CONFLICT` |
| any other | 500 | `INTERNAL` (generic message, no internals) |

A `@ControllerAdvice` for the identity filter (401) writes the envelope directly (filter is outside the MVC advice). No `@ExceptionHandler` leaks `e.getMessage()` for 5xx.

Supports: ARC-015, NFR-020, IR-001, PRN-004, PRN-003.

## DES-012 — Frontend (React + TypeScript) (FR-001, FR-050, FR-052, FR-053, NFR-050)

**Framework**: React 18, TypeScript 5, Vite 5. No router needed (single page, tab-driven). No state library — local state in components and a single `IdentityContext` for the selected identity. No CSS framework (a small `styles.css`) — NFR-050 says "a simple textarea and plain JSON viewer are sufficient."

**Components** (from DES-000):

- `IdentitySelector` — a `<select>` for the two fixed identities (`demo-author` / `demo-consumer`). The current identity is shown prominently (FR-001: "show the current identity in the UI"). Labeled "demo-only". On change, the app re-fetches data and re-evaluates which actions are visible.
- `FilterBar` — `domain` and `risk` dropdowns + "apply". Both optional.
- `SopList` — table of current published SOPs (`sop_id`, `title`, `version`, `domain`, `risk`). Shows empty state when `[]`. Row click → detail view. Keyboard: rows are `<a>` with proper `href`.
- `DraftList` — (author only) table of saved drafts (`sop_id`, `revision`, `publish_failed` indicator). Row click → editor with that draft loaded.
- `Editor` — a `<textarea>` for the source. Buttons: "Insert template", "Save draft", "Validate / preview", "Publish". Publish is disabled and shows a helper text if `unsaved` is true (FR-010, spec §5.3 "UI requires unsaved edits to be saved first"). After a successful save, the revision label is shown.
- `ValidationPanel` — renders the `issues` list from `POST /validate`, grouped by `stage` (structural vs semantic). Each issue shows `stage`, `code`, `message`, and `path`. Color is not the only signal — a text label `[STRUCTURAL]`/`[SEMANTIC]` precedes each issue (NFR-050).
- `HumanView` — renders the canonical `content` object as structured HTML with **all** user-controlled strings passed as React text children (auto-escapes HTML). Sections:
  - Identity: "SOP `BILL-REFUND-001` · version `2` · Owner `Billing Operations`".
  - Domain/risk chips.
  - **Policy** → `use_when` / `do_not_use_when` rendered as bulleted lists.
  - **Inputs** table → `name`, `type`.
  - **Rules** table → `id`, conditions as a conjunction, `action_ids` as a list.
  - **Actions** table → `id`, `kind`, `description`, `max_amount` (refund only).
  - **Boundaries** section → `escalation` list, each with `action_id`, `input`, `op`, `amount`, `target_action_id`.
  - **Customer messages** → `primary`, `escalation` as quoted blocks, each with a visible label "Describes the SOP; this app does not execute a real action" (FR-052: "Labels must make clear that action and message content describes an SOP and does not report a real action").
- `JsonView` — a `<pre>` containing `JSON.stringify(envelope, null, 2)` (the full envelope, including `sop_id`, `version`, `published_at`, `content`). No syntax highlighting (NFR-050 simple).
- `Toast` — a single line at the top of the page for status / errors (save succeeded / failed, publish failed with the top issue, "You must save first before publishing"). Not color-only (NFR-050).

**API client** (`src/api/client.ts`): a `fetch` wrapper that:
- Always sends `X-Demo-User: <currentIdentity>` and `Content-Type: application/json`.
- Reads the `Content-Type` of the response; non-JSON → throw `ApiError(500, "…")`.
- On `!ok`, parse the body, and throw a typed error with `code`/`message`/`issues`.
- 401/403 are thrown with a message from the body (FR-001 enforcement visible in the UI).

**State**: local to `App.tsx`. `identity`, `view` (`list | editor | human | json`), `currentSopId`, `draftsList`, `sopsList`, `currentSop` (the fetched envelope, shared by the human and JSON views). "Publish during viewing does not mix versions" (FR-053) is enforced because both the human and JSON views render from `currentSop` in memory; a `publish` call triggers a fresh `fetch` to refresh `currentSop` (so the view updates on the next user action).

**Keyboard**: every interactive element is a `<button>` or `<a>` (no `div onClick`). Focus order matches DOM order. `tabindex` not used unless necessary. Screen-reader friendly: `<label>` for inputs; `aria-live="polite"` on the `Toast`; `role="alert"` on the `ValidationPanel` structural/semantic blocks.

Supports: ARC-001, FR-001, FR-050, FR-052, FR-053, NFR-050, PRN-004 (no HTML execution — React escapes text by default).

## DES-013 — Docker Compose and service images (ARC-013, NFR-001)

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: sop
      POSTGRES_PASSWORD: sop          # local-only; never in source outside compose
      POSTGRES_DB: sop
    ports: [ "5433:5432" ]            # host:5433 -> avoid clash with host pg
    volumes: [ "sop_pgdata:/var/lib/postgresql/data" ]
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U sop -d sop" ]
      interval: 2s
      timeout: 2s
      retries: 20
  backend:
    build:
      context: ./backend
      target: runtime
    depends_on:
      db: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: demo
      SPRING_DATASOURCE_URL: "jdbc:postgresql://db:5432/sop"
      SPRING_DATASOURCE_USERNAME: sop
      SPRING_DATASOURCE_PASSWORD: sop
      APP_CORS_ALLOWED_ORIGIN: "http://localhost:8075"
    ports: [ "8080:8080" ]
    healthcheck:
      test: [ "CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1" ]
      interval: 3s
      timeout: 3s
      retries: 30
  frontend:
    build:
      context: ./frontend
    depends_on:
      backend: { condition: service_healthy }
    ports: [ "8075:80" ]
    healthcheck:
      test: [ "CMD-SHELL", "wget -q -O /dev/null http://localhost:80/healthz || exit 1" ]
      interval: 3s
      timeout: 3s
      retries: 10
volumes:
  sop_pgdata:
  ```

Ports: `5433` (Postgres host port), `8080` (Spring), `8075` (nginx UI). Healthchecks use the standard commands; `depends_on.condition: service_healthy` ensures startup order (PRN-007 "waits for readiness rather than fixed sleeps").

**`backend/Dockerfile`** (multi-stage):
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -f pom.xml -DskipTests package \
    -Dmaven.repo.local=.m2/repository

FROM eclipse-temurin:21-jre
COPY --from=build /app/target/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**`frontend/Dockerfile`** (multi-stage):
```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY . .
RUN npm ci && npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
ENTRYPOINT ["nginx", "-g", "daemon off;"]
```

`nginx.conf` serves the static UI on port 80, plus a `/healthz` endpoint (a small static file in the UI build output for the compose healthcheck) and a `/api/` reverse-proxy to `http://backend:8080` so the browser only ever talks to origin `http://localhost:8075` (no CORS across a browser origin is required at all — the browser sees one origin, the UI's own origin). The CORS config in the backend is defense-in-depth for direct backend hits.

Supports: ARC-013, NFR-001, PRN-003, PRN-007.

## DES-014 — Test strategy (NFR-041, PRN-008)

**Layers** (per NFR-041):
1. **Unit tests** (backend, `mvn test`) — pure functions: `SafeYaml`, `SectionParser`, `FrontMatterValidator`, `SectionValidator`, `TypeValidator`, `ReferenceValidator`, `FinancialValidator`, `CanonicalBuilder`, `IdentityFilter` role check, `GlobalExceptionHandler` mappings.
2. **Integration tests** (backend, Testcontainers PostgreSQL) — `@SpringBootTest` with a `PostgreSQLContainer` pinned to `postgres:16-alpine`. Covers:
   - Draft save/revision increment / idempotent seed
   - `POST /validate` on a valid, missing-limit, missing-escalation, hostile alias, custom tag, deep nesting, non-finite number, unknown field, duplicate heading, bad section heading, malformed YAML
   - Identity: missing header (401), unknown identity (401), consumer calling author-only op (403)
   - Publish: success (version 1), stale revision (409), duplicate publish (409), validation failure (422 + indicator set), indicator cleared on next save, consumer 404 when nothing published
   - Concurrent publishes (2 threads → 2 distinct versions, no interleaved state)
   - `GET /sops` filters (domain, risk, both, none) and `sop_id` sort
   - `GET /sops/{id}/versions/{v}` historical
   - Immutable snapshot after edit-and-publish
   - Human/JSON consistency: both read the same `content` from the same row
3. **Front-end tests** (Vitest + React Testing Library) — `Editor` unsaved guard, `IdentitySelector` persistence, `DraftList` author-only visibility, `ValidationPanel` structural/semantic split, `HumanView` renders strings (verify a `<script>` string shows up as visible text not executed), `JsonView` renders the full envelope including `sop_id`, `version`.
4. **Type check** — `npx tsc --noEmit` (root `make` runs it before the production build).
5. **Lint** — `npm run lint` (ESLint, recommended TS rules).
6. **Production build** — `npm run build` (Vite).

**Test coverage requirements** (from NFR-041): safe parsing (aliases, custom tags, deep nesting, `.inf`/`.nan`), missing financial limit, missing escalation, identity enforcement, atomic publication and immutable versions, invalid replacement (indicator + previous version retained), human/JSON consistency, representative hostile YAML, and hostile HTML strings in action descriptions.

**Tooling**:
- Backend: Spring Boot 3.5.x + JUnit 5 + AssertJ + Mockito + Testcontainers 1.20.4 + Flyway 11.7.2.
- Frontend: React 18, TypeScript 5, Vite 5, Vitest 2, Jest-dom, Testing Library, ESLint 8 + `@typescript-eslint`.
- Root: `make verify` (runs backend + frontend + typecheck + lint + build), `make demo` (`docker compose up --build -d`), `make smoke` (curl-driven primary journey against the running Compose stack), `make down-clean` (`docker compose down -v`).

**Smoke script** (`scripts/smoke.sh`): waits for the three services to be healthy, then runs the primary journey against the compose stack:
1. `PUT /drafts/BILL-REFUND-001` with the template → 200 with revision 1.
2. `POST /validate` → 200 `valid: true`.
3. `POST /sops/BILL-REFUND-001/publish` with `revision: 1` → 200, `version == 1`.
4. `GET /sops?domain=Billing&risk=medium` → includes `BILL-REFUND-001`, `version == 1`.
5. As `demo-consumer`: `GET /sops/BILL-REFUND-001` → 200 (identical envelope).
6. As `demo-consumer`: `POST /sops/BILL-REFUND-001/publish` with `revision: 1` → 403 `FORBIDDEN`.
7. As `demo-author`: `GET /sops/BILL-REFUND-001/versions/1` → 200 (identical envelope).

Any failure → non-zero exit with the failing step and response body.

Supports: NFR-041, PRN-008, PRN-005, PRN-006.

## DES-015 — Assumptions (traceable)

| # | Assumption | Source | Risk |
|---|------------|--------|------|
| A-1 | `POST /validate` on invalid content does NOT set `publish_failed_at` — the indicator is set only by a failed publish. | ARCHITECTURE Q-2, FR-034 "Invalid preview requests do not change any persisted state." | Low — aligns with the explicit FR-034 wording. |
| A-2 | `GET /sops` with no filter returns all current SOPs. | IR-001 "Return all matching current SOPs" — with no filter, all. | Low. |
| A-3 | The seed initializer runs on `demo` profile only (Compose sets it). `make verify` runs without the profile (no seed). | DR-003, NFR-001. | Low. |
| A-4 | `drafts` table has one row per `sop_id`; there is no `author` equality check. | ARCHITECTURE Q-1; there is a single author identity in the demo. | Medium — if multiple authors are ever added, the list would need a per-author filter, but the requirement text does not specify one. |
| A-5 | `published_at` is the transaction timestamp (UTC) of the publish. | ARCHITECTURE Q-4, spec §4 "published_at is an illustrative UTC timestamp assigned by the server." | Low. |
| A-6 | `drafts.revision` starts at 1 on first save and increments on every subsequent save. | ARCHITECTURE Q-3, FR-010. | Low. |
| A-7 | `sops/{id}/versions/{version}` (author) and `GET /sops/{id}` (either) share the same DTO and both read from `publications`. | IR-001, spec §4 "Historical snapshots include the same envelope as current snapshots." | Low. |
| A-8 | The `GET /drafts` list is not filtered by author. | A-4. | Low. |
| A-9 | `POST /sops/{id}/publish` re-validates the saved draft source (not a client-supplied source). | ARCHITECTURE Q-7 "raw source" + FR-042 "The backend revalidates that source." | Low. |
| A-10 | `413` (oversized source) is returned by `PUT /drafts/{id}`. `POST /validate` with an oversized source is 400 `MALFORMED` (because IR-001 says "malformed request envelopes return 400"). | IR-001. | Low — the `POST /validate` request body carries `source`; a 413 on that endpoint would only be triggered by the request body size, which the servlet would reject before the controller. |
| A-11 | `413` for `PUT /drafts/{id}` is checked before upsert (so no row is written if too large). | IR-001 413, FR-010. | Low. |
| A-12 | The seed uses `sop_id BILL-REFUND-001` exactly as `spec.md §3`. | DR-003, spec §3. | Low. |
| A-13 | A valid template is available via `POST /validate` and can be inserted by the UI "Insert template" button (client-side only, no API needed). | spec §5.3 "template insertion". | Low. |
| A-14 | `POST /validate` request body is only `{source}`; the response envelope is exactly `{valid, issues, content}` (per IR-001). | IR-001, spec §4. | Low. |
| A-15 | Identity header name is exactly `X-Demo-User`. | IR-001 "sent via `X-Demo-User`". | Low. |
| A-16 | When the `GET /sops` filter `domain`/`risk` is passed with a value not in the allowed set, the response is 400 `INVALID_FILTER` — not a 404 or empty list. | IR-001 "invalid filter values return 400". | Low. |
| A-17 | The seed and all tests use fictional data and no real customer PII. | NFR-050, DR-003 "fictional data only". | N/A. |
| A-18 | `published_at` in the envelope is a string (ISO-8601 UTC), not a number. | spec §4 (envelope JSON shows a string). | N/A. |

## DES-016 — Notable abstractions and why they are the smallest sufficient choice

Per the pattern-selection rule, each notable abstraction is justified against a concrete design problem. Nothing below is speculative.

| Abstraction | Problem it solves | Chosen approach | Simpler alternative rejected, and why |
|-------------|-------------------|-----------------|----------------------------------------|
| `SafeYaml` (DES-003) | SnakeYAML 2.4 silently inlines scalar aliases, has no depth limit, and accepts `.inf`/`.nan`; authors are untrusted (PRN-004). | Token-scan for `AliasToken` + `SafeConstructor` + a recursive depth/finite-number walk. | Using SnakeYAML loader options alone is **insufficient** (verified empirically: scalar aliases pass). A custom tag-inspector does not cover aliases/depth. Hence the walk is required, not ornamental. |
| `ValidatorPipeline` (DES-005) — a fixed ordered stage list | Many independent checks must run deterministically, each producing path-stamped issues (PRN-005). | A plain object wiring the stages in a documented order; no plugin/registration mechanism. | A plugin system / strategy registry would be speculative extensibility; the format is frozen, so a fixed order is simpler and more testable. |
| `IdentityFilter` (DES-007) as a servlet `Filter` | Identity must be checked **on every API request** (FR-001) before any controller logic. | One servlet filter for 401 + a 3-line role check helper used by each controller for 403. | Spring Security is a full authorization framework — overkill for two fixed local identities and would add a dependency with no benefit (PRN-003 "avoid speculative interfaces and layers"). |
| Advisory lock for publication (DES-002, DES-008b) | Concurrent publishers of the same SOP must not produce conflicting current versions (FR-042) without distributed infrastructure (PRN-006). | `pg_advisory_xact_lock(hashtext(sop_id))` + DB unique constraints. | A lock table, a message broker, or an application-level mutex would add infrastructure for a single-node local demo; an advisory lock is Postgres-native and transaction-scoped. |
| `@Profile("demo")` seed initializer (DES-010) | Seed must start the demo but never duplicate/overwrite on restart (DR-003, AC-E2E-005). | An `ApplicationRunner` gated by profile + an empty-table guard. | A second Flyway seed migration (`V2`) would clobber state and couple seed to migration order; a raw `docker-entrypoint` SQL load would not be testable. The guarded runner is the smallest correct choice. |
| `fetch`-based API client (DES-012) | The frontend must send `X-Demo-User` and surface typed errors (FR-001, FR-034). | A ~50-line `fetch` wrapper returning typed `ApiError`. | axios/ky add a dependency; for 8 local endpoints `fetch` is sufficient (NFR-050, PRN-003). |
| Records as DTOs (DES-009) | Request/response shapes must be stable and serialized deterministically. | Java 21 `record`s + Jackson. | Custom builder/DTO pairs are more code for no benefit. |

Everything not listed above is deliberately **not** abstracted: no repositories beyond the three Spring Data interfaces (the tables are simple), no service-layer interfaces (callers reference the concrete class), no event bus, no cache, no message queue, no object storage. This keeps the design minimal while satisfying every requirement and the five acceptance journeys.
