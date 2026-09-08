# SOP Demo

A local demo that takes an author's structured Markdown through
**validate → publish → read the published version** in human and JSON views, built with
Java 21 / Spring Boot, React + TypeScript, and PostgreSQL, started with Docker Compose.

It is explicitly a **demo**, not a production system: identities are a labeled local
mechanism, and authored rules/actions are only validated and displayed — they are never
executed and no external business actions are performed.

## Prerequisites

- Docker + the Docker Compose plugin (v2).
- `make` (for the `make …` targets).

## Quick start

```sh
make demo    # build + start the seeded stack; waits until all three services are healthy
```

Once it is healthy, open **http://localhost:13000**.

### URLs

| Service  | Host URL                              | Notes |
|---|---|---|
| Frontend (UI) | http://localhost:13000 | nginx; proxies `/api` → backend |
| Backend (API) | http://localhost:18080 | Spring Boot; `/healthz` liveness |
| PostgreSQL  | localhost:15432 | db `sopdemo`, user `sopdemo` |

Ports are overridable via `SOPDEMO_UI_HOST_PORT`, `SOPDEMO_BACKEND_HOST_PORT`,
`SOPDEMO_DB_HOST_PORT` (defaults 13000 / 18080 / 15432 — deliberately non-default).

## Identities (local-only)

Two fixed demo identities are used, selected visibly in the UI and sent to every API
request as the `X-Demo-User` header. This is a **demonstration mechanism, not production
authentication** — there are no real credentials and no secret management.

| Identity      | Role |
|---|---|
| `demo-author`   | save/validate/publish drafts and read the catalog |
| `demo-consumer` | read published content only |

Rules (enforced server-side on every request):
- Missing/unknown identity → `401`.
- `demo-consumer` attempting an author operation (save/validate/publish/version read) → `403`.
- Consumers never receive drafts.

## The five acceptance journeys

1. **AC-E2E-001** — Author inserts `spec.md`'s template, saves, validates, and publishes.
   Version 1 is stored and the SOP appears in domain/risk-filtered results.
2. **AC-E2E-002** — Remove the refund limit and the escalation boundary. Validation
   returns separate, readable safety issues; publication is rejected; fixing both lets
   it publish.
3. **AC-E2E-003** — Open human and JSON views of a published SOP as the consumer.
   Both show the same canonical identity/version/policy; direct consumer mutation returns `403`.
4. **AC-E2E-004** — Save an invalid replacement and attempt publish (version 1 stays
   current, the author is told it failed and the indicator is set); then correct and
   publish (version 2). Version 1 remains unchanged and retrievable.
5. **AC-E2E-005** — From a clean checkout, `make demo` brings up three healthy services
   with the seeded draft; run the author→consumer flow; restart the app and drafts plus
   published snapshots are retained.

## API (all under `/api/v1`)

| Method & path | Access | Contract |
|---|---|---|
| `POST /validate` | author | `{source}` → `{valid, issues, content}` (`content` is null when invalid); `200` even with issues |
| `PUT /drafts/{sop_id}` | author | `{source}` → stored draft with a server-assigned `revision` |
| `GET /drafts` | author | draft summaries with `revision` + publication-failure indicator |
| `GET /drafts/{sop_id}` | author | saved source, `revision`, failure indicator |
| `POST /sops/{sop_id}/publish` | author | `{revision}` → published canonical snapshot |
| `GET /sops?domain=Billing&risk=medium` | either | current published summaries (AND filters; `400` on invalid values) |
| `GET /sops/{sop_id}` | either | current canonical snapshot |
| `GET /sops/{sop_id}/versions/{version}` | author | an immutable historical snapshot |

Errors use a fixed `{code, message, issues}` envelope with stable statuses:
`400` malformed/invalid filter, `401`/`403` identity, `404` absent, `409` revision/duplicate
conflict, `413` oversized source, `422` rejected publish content, and a generic `500`
with no internals.

## Shutdown & data removal

```sh
make down    # stop the stack (keeps the data volume; restarts keep drafts + publications)
make clean   # stop the stack AND remove the named data volume (explicit data deletion)
```

The PostgreSQL data lives in the named Compose volume `sopdemo_pgdata`; `make clean`
(`docker compose down -v`) deletes it. No data lives anywhere else.

## Assumptions

- The two demo identities above are fixed local configuration — they are deliberately
  simple and labeled; do not reuse this identity model for real access control.
- The seed (the duplicate-charge example) is enabled only on the demo profile and is
  idempotent — it never duplicates a seeded draft or overwrites an author's edits.
- Host ports 18080/13000/15432, db/user/password all `sopdemo`, and the UI origin
  `http://localhost:13000` (the value CORS allow-lists) are the local demo defaults.

## Verification (see `make …` / `VERIFICATION.md`)

- `make demo` — bring up the stacked seeded demo and wait for readiness.
- `make verify` — backend unit + integration (PostgreSQL), frontend test/typecheck/lint/build.
- `make smoke` — bring up Compose and exercise the primary author→consumer journey; exits
  nonzero on failure.
- `make down` / `make logs` / `make clean` — stop / follow / remove (volume) the stack.
