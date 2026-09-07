# SOP Demo — API Reference

Base path: `/api/v1` (relative to the backend origin). All requests **require**
the `X-Demo-User` header (`demo-author` or `demo-consumer`). JSON in/out.

## Identities & status codes

| Header value  | Role     | May access                                    |
|---------------|----------|-----------------------------------------------|
| `demo-author` | Author   | everything                                    |
| `demo-consumer` | Consumer | list, detail (published snapshot only)      |

Status codes: `200` success · `400` malformed request / invalid filter ·
`401` missing/unknown identity · `403` permission denied · `404` absent
· `409` revision conflict (stale or duplicate publish) · `413` oversized source
· `422` rejected publication content · `500` unexpected (generic, no internals).

Error body (all errors):

```json
{ "code": "STALE_REVISION", "message": "draft revision 1 is stale …", "issues": [] }
```

`code` values: `MALFORMED`, `INVALID_FILTER`, `MISSING_IDENTITY`, `FORBIDDEN`,
`NOT_FOUND`, `STALE_REVISION`, `PUBLICATION_CONFLICT`, `SOURCE_TOO_LARGE`,
`VALIDATION_FAILED`, `INTERNAL`.

---

### `POST /validate` (Author)

Validate a source **without** publishing. Returns `200` whether or not the
content is valid; a malformed envelope is `400`.

- Request: `{ "source": "<markdown+yaml>" }`
- Response: `{ "valid": boolean, "issues": Issue[], "content": Content | null }`
  - `valid` true → `content` present, `issues` = `[]`
  - `valid` false → `content` = `null`, `issues` non-empty
- `400 MALFORMED` if `source` missing/blank; `403` for consumer.

`Issue`: `{ "code", "stage": "structural"|"semantic", "message", "path" }`
(stable order by `path` then `code`).

---

### `PUT /drafts/{sop_id}` (Author)

Create or update the single editable draft for `sop_id`.

- Request: `{ "source": "<markdown+yaml>" }`
- Response: `{ "sop_id", "revision", "source", "publish_failed", "updated_at" }`
  - new → `revision` 1; each subsequent save → `revision += 1`
  - `publish_failed` cleared on save
- `413 SOURCE_TOO_LARGE` if `source` > 65,536 UTF-8 bytes; `403` for consumer.
  Saving is allowed for **incomplete** drafts (within the size limit).

---

### `GET /drafts` (Author)

- Response: `[{ "sop_id", "revision", "publish_failed" }, …]` sorted by `sop_id`.
- `403` for consumer.

### `GET /drafts/{sop_id}` (Author)

- Response: `{ "sop_id", "revision", "source", "publish_failed", "updated_at" }`
- `404 NOT_FOUND` if absent; `403` for consumer.

---

### `POST /sops/{sop_id}/publish` (Author)

Publish the saved draft (by exact revision). Re-validates the **saved** source.

- Request: `{ "revision": <int> }`
- Response: the stored envelope:
  `{ "sop_id", "version", "published_at", "content" }`
- Errors:
  - `404` no draft for `sop_id`
  - `409 STALE_REVISION` requested ≠ stored revision
  - `422 VALIDATION_FAILED` saved source invalid (previous version retained;
    draft `publish_failed_at` set, body carries the issues)
  - `409 PUBLICATION_CONFLICT` same `(sop_id, draft_revision)` already published
  - `403` for consumer

---

### `GET /sops?domain=&risk=` (Either)

List **current** published SOPs. `domain` ∈ {`Billing`,`Support`},
`risk` ∈ {`low`,`medium`}; each present filter is **AND**-ed; sort by
`sop_id` ascending. Empty result → `[]`.

- Response: `[{ "sop_id", "title", "version", "domain", "risk" }, …]`
- `400 INVALID_FILTER` for an out-of-set `domain`/`risk`.

### `GET /sops/{sop_id}` (Either)

Current canonical snapshot.

- Response: `{ "sop_id", "version", "published_at", "content" }`
- `404` if nothing published for `sop_id` (never substitutes draft data).

### `GET /sops/{sop_id}/versions/{version}` (Author)

Immutable historical snapshot.

- Response: `{ "sop_id", "version", "published_at", "content" }`
- `404` if that version is absent; `403` for consumer.

---

## Content shape (spec §4)

`content` is exactly: `sop_id, title, owner_team, domain, intent, risk_level,
max_autonomy, policy{use_when[], do_not_use_when[]}, inputs[], rules[],
actions[], boundaries{escalation[]}, customer_messages{primary, escalation}`.
The envelope's `sop_id` must equal `content.sop_id`. Version and timestamps are
server-owned metadata and are never authored.

## CORS

Limited to the local UI origin `http://localhost:8075` (defense-in-depth for
direct backend hits; the browser normally talks only to that origin via the
nginx proxy).
