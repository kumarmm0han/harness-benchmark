# SOP Demo

A local-only demo of a small standard-operating-procedure (SOP) publishing app:
an **author** writes a structured Markdown SOP, **validates** it, **publishes**
an immutable canonical JSON snapshot, and a **consumer** reads that snapshot in
a **human** view and an **AI/JSON** view.

Built with Java 21 / Spring Boot 3.5, React 18 + TypeScript + Vite, and
PostgreSQL, started with Docker Compose. **No external services, no real
credentials, no real customer data, and no execution of any authored rule or
action** — the app only validates and displays declared policy.

> This is a labeled **demo** of the author→consumer flow. The two identities
> are a local selection mechanism (sent as the `X-Demo-User` header), not
> production authentication.

## Quick start

Prerequisites: Docker + Docker Compose, and either `make` (recommended) or
the documented commands. Maven/Node are used only if you build outside Docker.

```bash
make demo        # build + start db, backend, frontend (all healthchecked)
```

Then open the UI:

| What            | URL                          |
|-----------------|------------------------------|
| Frontend (UI)   | `http://localhost:8075`      |
| Backend API     | `http://localhost:8080/api/v1/...` |
| Postgres (host) | `localhost:5433` (user/pass/db: `sop`) |

`make smoke` runs the primary author→consumer journey against the running stack.
`make verify` runs the full test/type/lint/build suite.

## Choosing your identity

The UI has an **Identity** selector labeled *demo-only* with two fixed accounts:

- **`demo-author`** — can edit, save, validate, publish, and read.
- **`demo-consumer`** — reads published content only.

Every API request carries `X-Demo-User: <identity>`. The backend enforces it on
every request:

- missing or unknown identity → **401**
- consumer calling an author-only operation → **403**

This is local-only and fixed (`app.demo.author` / `app.demo.consumer` in config).

## The five acceptance journeys

1. **AC-E2E-001 — author publishes.** As `demo-author`, open **Editor** →
   *Insert valid template* → *Save draft* → *Validate / preview* → *Publish*.
   Version 1 is persisted and the SOP appears in the domain/risk-filtered list.
2. **AC-E2E-002 — invalid publish is rejected.** Remove the refund limit
   (`max_amount`) and the escalation boundary, then *Validate*. You get separate,
   readable safety issues (`FIN_REFUND_MAX_AMOUNT`, `FIN_ESCALATION_MISSING`).
   *Publish* is rejected (422) and the previous version stays current; correcting
   both and publishing succeeds.
3. **AC-E2E-003 — human + JSON views, consumer read-only.** As `demo-consumer`,
   open a published SOP. **Human view** and **JSON view** show the *same*
   `sop_id` + `version` + content. Attempting a publish as a consumer → **403**.
4. **AC-E2E-004 — failed attempt preserves the previous version.** Publish v1,
   save an invalid draft, attempt publish (rejected, failure indicator shown),
   correct it and publish v2. **v1 is unchanged and still retrievable.**
5. **AC-E2E-005 — restart retains data.** `make down` then `make demo` again:
   drafts and publications survive; the seed is never duplicated.

## Data & lifecycle

- One editable **draft** per SOP; saving increments its `revision` and never
  touches published content.
- Publishing re-validates the saved draft and stores an **immutable** snapshot
  (source + canonical content + assigned version + UTC timestamp) and points
  `sop_current` at it. Consecutive integer versions per SOP.
- `GET /sops/{id}` returns the **current** snapshot; `GET /sops/{id}/versions/{n}`
  (author) returns an immutable historical one.
- If nothing is published, the consumer detail is **404** — it never substitutes
  draft data.

## Shutdown and data deletion

```bash
make down          # stop + remove containers & network; KEEPS the volume (data persists)
make down-clean    # stop + remove containers AND the `sop_pgdata` volume
                   # → DELETES ALL DEMO DATA (drafts, publications, database)
```

**`make down-clean` is the explicit "delete all data" command.**

## Verification

```bash
make verify        # backend unit+Postgres integration tests,
                   # frontend Vitest, tsc typecheck, ESLint, Vite prod build
make smoke         # end-to-end primary journey against the running stack
```

Tests use genuine PostgreSQL (Testcontainers-style local container for the
backend integration tests; the Compose `postgres:16-alpine` for the demo) and
cover safe parsing (aliases, tags, depth, non-finite numbers, duplicate keys),
reference + financial-safety validation, identity enforcement, atomic
publication / immutable versions, invalid replacement, and hostile YAML/HTML.
See `VERIFICATION.md` for actual command outcomes.

## Local-only disclaimer

- Fictional data only; the seed (`BILL-REFUND-001`) is a made-up policy.
- No external network, no secrets in the source (the compose file holds only
  local-only placeholder credentials for the demo).
- Authored rules/actions/messages are **displayed, never executed**.
