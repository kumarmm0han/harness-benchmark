# SOP Demo — author → validate → publish → read

A local, end-to-end demo of one complete journey:

**Author a structured Markdown SOP → validate → publish an immutable canonical JSON version → read that same published version in a human view and a JSON view.**

Runtime: Java 21 / Spring Boot (backend) + React 18 / TypeScript (frontend) + PostgreSQL 16, all started with Docker Compose. `PRINCIPLES.md`, `REQUIREMENTS.md`, and `spec.md` are the frozen inputs this demo implements.

This is a **labeled, local-only demo**: the two identities are a demo mechanism (not real authentication), the seed and journey data are fictional, and **no authored rule, action, refund, or external business action is ever executed** by this application.

## URLs and identities

| What | Where |
|---|---|
| UI (author + consumer screens) | http://localhost:3010 |
| Backend API base path | http://localhost:8080/api/v1 (also proxied at http://localhost:3010/api/v1) |
| PostgreSQL (host) | localhost:5432, db/user/password `sopdemo` (local-only default, documented per NFR-020) |

Identities are fixed configuration (`DR-003`) and selected in the UI's "Demo identity" fieldset:

- `demo-author` — saves drafts, validates, publishes, reads current + historical snapshots.
- `demo-consumer` — reads published content only.

Every API request must carry the identity header `X-Demo-User: demo-author` or `X-Demo-User: demo-consumer`. Missing/unknown identities get `401`; a consumer attempting an author operation gets `403`. The backend checks identity on **every** API request regardless of client behavior (`FR-001`).

## Quickstart

```
make demo          # builds + starts db, backend, frontend; waits until all 3 are healthy
```

Then open http://localhost:3010, and use the demo identity selector (top of the page).

Requirements to run the app: Docker (with Compose) and `make`. Requirements to run the full verification (`make verify`): Docker, JDK 21, Maven 3.6+, Node 22 / npm (backend unit + integration tests run against a Testcontainers PostgreSQL started automatically by the backend build).

The stack keeps drafts and publications across normal restarts (named volume `sop-pg`); the seeded draft appears once (demo profile) and is never duplicated or overwritten on repeated startups (`DR-003`).

## The five acceptance journeys

### AC-E2E-001 — Author: template → save → validate → publish
1. Identity: `demo-author`. Open **New draft**.
2. Click **Insert sample template** (the spec.md template, sop_id `BILL-REFUND-001`), adjust `sop_id`/text if you want your own SOP.
3. Click **Validate &amp; preview** — you should see "Valid" plus the deterministic canonical JSON preview.
4. Click **Save draft** — note the saved revision.
5. Click **Publish** — the UI saves first if needed, publishes the saved revision, reports "Published version 1", and jumps to the published SOP.
6. As `demo-consumer`, the SOP appears in **Published SOPs** and is returned by `GET /api/v1/sops?domain=Billing&risk=medium`.

### AC-E2E-002 — Safety issues block publication
1. In the author editor, remove `max_amount` from the refund action **and** remove the `Boundaries` section (or the escalation entry).
2. **Validate &amp; preview** — two separate, readable semantic issues appear: `refund-limit-missing` and `refund-escalation-missing`, each with stage, message, and source path.
3. **Publish** — rejected with `422` and those issues; the previously published version (if any) remains current.
4. Restore both, save, publish again — now it succeeds.

### AC-E2E-003 — Consumer: human and JSON views of the same snapshot
1. Identity: `demo-consumer`. Open the published SOP.
2. Switch **Human view** ↔ **JSON view** — both render from the *same fetched snapshot* (identity + version + content), so a concurrent publish cannot mix versions. All authored strings render as plain text (no HTML/script execution); labels make clear this is declared SOP policy, not executed actions.
3. Direct consumer mutation (`PUT /drafts/…`, `POST …/publish`) returns `403`.

### AC-E2E-004 — Failed replacement preserves version 1
1. (After a version 1 exists) Edit the draft so validation fails (e.g. drop the escalation boundary), save, and publish → `422`; the draft list marks **publication failed** and consumers still see version 1.
2. Fix the draft (both issues if applicable), save (clears the failure flag), publish → **version 2** is current.
3. As author, open the SOP detail and use the version picker to load **version 1** — the old snapshot is byte-identical to before; `GET /api/v1/sops/{id}/versions/1` returns it for verification.

### AC-E2E-005 — Clean checkout → run → restart
```
git clone <repo> && cd <repo>
make demo        # 3 healthy services; the seeded draft BILL-REFUND-001 is visible to the author
```
Run the AC-E2E-001 → AC-E2E-003 flow in the UI, then `make stop` and `make demo` again: drafts and publications are retained (named volume), exactly one seeded draft exists, and the author→consumer flow still works. `make clean-data` removes the data volume explicitly.

## Shutdown and data removal

```
make stop         # stop the stack; drafts + publications retained in the named volume
make clean-data   # stop the stack AND delete the named volume (all demo data)
```

## Verification commands

```
make verify       # backend build + unit + PostgreSQL integration tests;
                  # frontend tests (Vitest+RTL), `tsc --noEmit`, ESLint, production build
make smoke        # HTTP smoke of the primary author→consumer journey against the running
                  # Compose stack (exits nonzero on failure; run after `make demo`)
```

- Backend: Java 21 + Maven; integration tests use Testcontainers PostgreSQL 16.
- Frontend: Node 22 + npm; Vitest + React Testing Library + jsdom, TypeScript strict, ESLint (react-hooks), Vite production build.

## API reference (base path `/api/v1`)

All requests require `X-Demo-User`. Errors share one shape: `{"code": "...", "message": "...", "issues": []}` (`issues: []` when no field issues apply). Response objects use snake_case.

| Method & path | Access | Request | Success |
|---|---|---|---|
| `POST /validate` | author | `{"source": "<markdown>"}` | `200 {"valid": bool, "issues": [...], "content": {...} \| null}` — `content` is `null` when invalid; valid even with content issues, `400`/`413` for malformed envelopes |
| `PUT /drafts/{sop_id}` | author | `{"source": "..."}` | `200 {"sop_id", "revision", "source"}` — incomplete drafts are allowed; revision increments per save; `413` oversized; `400` bad sop_id/envelope |
| `GET /drafts` | author | — | `200 {"drafts": [{"sop_id", "revision", "publish_failed"}, ...]}` sorted by `sop_id` |
| `GET /drafts/{sop_id}` | author | — | `200 {"sop_id", "revision", "publish_failed", "source"}`; `404` missing |
| `POST /sops/{sop_id}/publish` | author | `{"revision": <saved-revision>}` | `200 {"sop_id", "version", "published_at", "content"}` — envelope `sop_id` equals `content.sop_id`; `content` is the stored canonical snapshot |
| `GET /sops?domain=&risk=` | either | — | `200 {"sops": [{"sop_id", "title", "version", "domain", "risk_level"}]}` — filters AND-combined (`domain` ∈ `Billing|Support`, `risk` ∈ `low|medium`), sorted by `sop_id`; invalid filter values → `400` |
| `GET /sops/{sop_id}` | either | — | `200 {"sop_id", "version", "content"}` (current canonical snapshot); `404` when nothing published (drafts are never substituted) |
| `GET /sops/{sop_id}/versions/{version}` | author | — | `200 {"sop_id", "version", "published_revision", "published_at", "content"}` (immutable snapshot); `404` missing version |

Status code conventions: `400` malformed request / invalid filters, `401` missing/unknown identity, `403` identity lacks permission, `404` absent resource, `409` revision conflict (stale or duplicate publication), `413` oversized source (limit 64 KiB), `422` rejected publication content, `500` generic (no internals). Successful saves and publishes return `200`.

Issue objects: `{"code", "stage": "structural"|"semantic", "message", "path"}` — sorted by `path` then `code`, deterministic for identical sources.

Issue codes (stable strings — structural): `source-too-large`, `malformed-yaml`, `duplicate-key`, `alias-or-tag`, `nesting-too-deep`, `non-finite-number`, `missing-frontmatter`, `frontmatter-not-mapping`, `unknown-section`, `missing-section`, `section-order`, `invalid-bullet-section`, `empty-section`, `invalid-machine-section`, `wrong-section-type`, `missing-field`, `unknown-field`, `invalid-enum`, `invalid-id`, `invalid-type`, `empty-required-text`, `empty-required-list`.
Problem codes (semantic): `duplicate-id`, `bad-reference`, `type-mismatch`, `operator-mismatch`, `refund-limit-missing`, `refund-escalation-missing`, `refund-not-allowed`, `refund-intent-requires-billing`, `sop-id-mismatch`.
Publication conflict codes: `stale-revision`, `duplicate-publication`.

## Security & safety notes

- Identity: labeled local-only demo mechanism; enforced on every API request server-side. No real credentials in source; the Postgres default is a documented local demo credential.
- Content safety: SnakeYAML `SafeConstructor`, aliases/custom tags rejected, 64 KiB source cap, 20-level YAML nesting cap, documented sections/fields only; authored rules/actions are **never executed**; React renders all authored strings as text (no HTML/script injection).
- Integrity: PostgreSQL constraints + one transaction publish (`UNIQUE(sop_id, version)`, `UNIQUE(sop_id, source_revision)`), row-level locking — concurrent publishes of the same saved revision cannot both succeed.
- CORS is limited to the local UI origin `http://localhost:3010`.

## Repository map

- `backend/` — Spring Boot: identity filter, content engine (safe parse → structure → semantics → compile), drafts, atomic publication, read model, Flyway `db/migration`, tests.
- `frontend/` — React 18 + TypeScript + Vite: identity, list/filters, editor, human/JSON detail; Vitest + RTL tests.
- `docker-compose.yml`, `Makefile`, `tests/` — local operation (`demo`, `verify`, `smoke`, `stop`, `clean-data`).
- `PRINCIPLES.md`, `REQUIREMENTS.md`, `spec.md` — frozen inputs. `ARCHITECTURE.md`, `TECHNICAL_DESIGN.md`, `TASKS.md` — the implementation record.
