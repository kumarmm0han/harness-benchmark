# SOP demo

Author structured Markdown, validate it, and publish an immutable canonical JSON snapshot. Human and AI JSON views read the same selected snapshot. Rules, actions, and customer messages describe policy; this application never executes them.

## Start and operate

Prerequisite for the application: Docker Engine/Desktop with Docker Compose v2 (`--wait` support). Run from the repository root:

```sh
docker compose up --build
# Or start detached and wait until healthy:
make demo
```

Open http://localhost:5173. The API is http://localhost:8080/api/v1; health is http://localhost:8080/actuator/health. Exactly three Compose services run: frontend (React assets served by nginx), backend (Java 21/Spring Boot), and PostgreSQL 17. PostgreSQL has no exposed host port. The UI/backend ports bind only to localhost. Override `UI_PORT` / `API_PORT` if needed, e.g. `UI_PORT=5174 API_PORT=8081 make demo`. The configured CORS origin follows UI_PORT; use the localhost URL.

Select **demo-author** or **demo-consumer** using the visible **Demo-only identity** selector. Requests send `X-Demo-User`. This is local-only identity selection, not production authentication. Anyone who can reach the API can select an identity. The fixed PostgreSQL password is a fictional local demo default, not a real credential.

The explicit `demo` Spring profile, enabled in Compose, inserts the exact `spec.md` example as draft `BILL-REFUND-001`, revision 1, only if absent. Startup never overwrites an existing draft. There is initially no published content.

```sh
docker compose ps                    # all three should be healthy
docker compose restart               # preserves source and snapshots
docker compose down                  # stop/remove containers; preserve named volume
# Explicitly delete this demo's stored data only when desired:
docker compose down --volumes
```

Compose uses the project-scoped named volume `sop-data`. Restart or normal `down` retains drafts, failed-publication indicators, and published history. No automatic deletion or reset is performed.

## Five acceptance journeys

1. **AC-E2E-001:** As author, choose **Insert valid template**, **Save draft**, **Validate / preview**, then **Publish saved revision**. On a clean database this publishes version 1. Filter published SOPs by Billing and medium to find it. To create another SOP, change both Draft SOP ID and the source `sop_id`.
2. **AC-E2E-002:** Remove `max_amount: 200` from action A1 and replace the complete Boundaries YAML object with `escalation: []`. Validate: separate `REFUND_LIMIT` and `REFUND_ESCALATION` semantic issues explain both problems. Save and publish: backend rejects it. Restore both from the template, save, and publish successfully.
3. **AC-E2E-003:** Select consumer, open a published SOP, and switch **Human view** / **AI JSON view**. Both show identical identity, version and policy from one fetched envelope. There are no author controls. Direct consumer write requests return 403.
4. **AC-E2E-004:** After publishing version 1, save the invalid replacement from step 2 and publish. The editor reports failure with version 1 still available. Reload and reopen the draft: its source and failure indicator remain. Correct and save it, then publish version 2. Retrieve `/sops/{id}/versions/1` as author to verify version 1 is unchanged.
5. **AC-E2E-005:** From a clean checkout, run `make demo`, open the seed, and complete the author-to-consumer flow. Restart Compose, reopen the draft, and open its publication: source, revisions and snapshots remain. Seed startup preserves user edits.

Editor text survives save/network failures. Unsaved changes are labeled and publication is disabled until saved. Inserting a template replaces editor text; save work first. Identity switching clears the workspace. Saving drafts never changes published content, and preview never persists anything. Publication revalidates the exact saved revision even when the UI has not requested preview.

## Verification

`make verify` requires Java 21, Maven 3.8+, Node 22.12+, npm, Python 3, Bash, Make and Docker. It starts a disposable PostgreSQL container on a random loopback port, runs backend tests and package build, then frontend tests, TypeScript, ESLint and production build. The disposable test database is removed on script exit. No H2 or database mocks replace PostgreSQL behavior. The compiler-only command is `mvn -f backend/pom.xml -Dtest=CompilerTest test`; a bare Maven run skips integration tests unless DB_URL is supplied, so use `make verify` for complete verification.

Browser smoke requires Playwright Chromium and its OS libraries. One-time setup:

```sh
npm --prefix frontend ci
cd frontend
npx playwright install --with-deps chromium
cd ..
make verify
make demo
make smoke
```

`make smoke` verifies the live API through the frontend proxy, creates uniquely named fictional SOPs, restarts the three Compose services twice, checks persistence and immutable history, and runs real Chromium author/consumer journeys. It exits nonzero on any failure. Run it on this local demo stack: the restarts temporarily interrupt access. Browser tests need the unmodified valid seed for their seed check; they do not overwrite user-edited seed content. It does not delete existing data. `UI_PORT` applies to both API smoke and browser smoke. Download access is needed for initial dependencies, images and browser installation; the running app needs no external services.

See VERIFICATION.md for actual outcomes, coverage and limitations.

## API contract

Every path below is relative to `/api/v1`. All actual API requests require `X-Demo-User: demo-author` or `demo-consumer`; CORS preflight and health probes carry no identity. JSON request envelopes reject unknown fields, duplicate keys and implicit scalar conversion. All successful operations return 200, including invalid-content validation.

| Operation | Access | Request | Response |
|---|---|---|---|
| POST `/validate` | Author | `{"source":"..."}` | `{"valid":true,"issues":[],"content":{...}}`, or valid false/content null |
| PUT `/drafts/{sop_id}` | Author | `{"source":"..."}` | `{"sop_id":"...","revision":1,"source":"..."}` |
| GET `/drafts` | Author | — | array of `{sop_id,revision,failed_revision,current_version}` |
| GET `/drafts/{sop_id}` | Author | — | `{sop_id,revision,source,failed_revision,current_version}` |
| POST `/sops/{sop_id}/publish` | Author | `{"revision":1}` | canonical publication envelope |
| GET `/sops?domain=Billing&risk=medium` | Either | optional domain/risk | array of `{sop_id,title,version,domain,risk}` |
| GET `/sops/{sop_id}` | Either | — | current canonical envelope |
| GET `/sops/{sop_id}/versions/{version}` | Author | — | immutable historical envelope |

`failed_revision` is null or the saved revision that failed publication; `current_version` is null or the retained current version. Each save increments revision and clears failure. Version increments only for successful publication. Saves allow incomplete source up to 65,536 UTF-8 bytes. Publication checks that source `sop_id` equals the path ID. IDs follow `spec.md`. Lists are sorted by SOP ID; filters combine with AND and return all matches. Consumers never receive drafts or stored source.

Canonical envelope: `{sop_id,version,published_at,content}` with a UTC server timestamp. Content has exactly `{sop_id,title,owner_team,domain,intent,risk_level,max_autonomy,policy,inputs,rules,actions,boundaries,customer_messages}` as defined in the frozen `spec.md`; no lifecycle metadata is injected into content. Source is stored alongside each snapshot. Existing publication rows reject updates/deletes in PostgreSQL.

Errors have `{code,message,issues}`; issues is always an array. Each validation issue is `{code,stage,message,path}`, sorted by path and code. Stages are structural or semantic. Statuses: 400 malformed request/invalid filter; 401 missing/unknown identity; 403 forbidden operation/origin; 404 absent resource; 409 stale/duplicate publication; 413 oversized source; 422 invalid saved publication; generic 500 unexpected failure without internals. The API never returns stack traces or SQL. After structural failure, dependent semantic checks are omitted.

Example:

```sh
curl -H 'X-Demo-User: demo-consumer' 'http://localhost:8080/api/v1/sops?domain=Billing&risk=medium'
curl -H 'X-Demo-User: demo-author' http://localhost:8080/api/v1/drafts/BILL-REFUND-001
```

The frozen documents are PRINCIPLES.md, REQUIREMENTS.md and spec.md. ARCHITECTURE.md and TECHNICAL_DESIGN.md explain the implementation; TASKS.md records task verification and Git delivery.
