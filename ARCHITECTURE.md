# Architecture

Frozen inputs: PRINCIPLES.md, REQUIREMENTS.md, spec.md. Existing IDs are retained. The starter repository contains no application, build configuration, or tests.

## ARC-001 — Three local services
Supports: PRN-002, PRN-003, PRN-007; NFR-001, DR-003.
React/TypeScript is served by a static web server; Spring Boot/Java 21 owns the API, compilation, authorization and publication; PostgreSQL owns durable state. Docker Compose starts these three services with health dependencies and a named database volume. No external runtime service is needed. Demo-profile initialization inserts the exact example once without overwriting edits.

## ARC-002 — Untrusted source to deterministic content
Supports: PRN-001, PRN-004, PRN-005; FR-020, FR-021, FR-030, FR-032, FR-034, NFR-020.
A single compiler parses only the specified Markdown structures and safe YAML, then performs structural and semantic checks. It returns ordered issues or canonical content. Preview is stateless; publication invokes the same compiler on the saved source. Conditions, prose, messages and actions are data only. Source bytes, YAML depth, duplicate keys, aliases, tags and unknown fields are checked before compilation succeeds.

## ARC-003 — Transactional publication
Supports: PRN-006; FR-010, FR-042, FR-043, FR-045, DR-001.
One draft row per SOP stores revision and failure indicator. A row lock serializes saves and publications for that SOP. Immutable version rows store source and complete canonical envelopes. One current-version reference points to a version row. Constraints enforce unique versions and published draft revisions. Validation failure commits only the failure marker; stale/duplicate requests cannot change the pointer. Consumers read only published rows. Database failure rolls the publication back.

## ARC-004 — Explicit API security boundary
Supports: PRN-004; FR-001, IR-001, NFR-020.
Every /api/v1 request checks X-Demo-User. Both fixed identities can read current publications; only demo-author can access drafts, validate, publish and read history. The mechanism is deliberately local demo identity, not authentication suitable for deployment. CORS permits only the configured UI origin. Controlled error envelopes never expose parser or database internals.

## ARC-005 — Author and consumer experience
Supports: PRN-001, PRN-002; FR-010, FR-034, FR-045, FR-050, FR-052, FR-053, NFR-050.
The author chooses a template, edits and saves source, previews validation, and publishes a saved revision. Draft discovery and failure state survive restart. Published list filters are AND-combined and sorted by ID. Human and JSON detail render the same fetched immutable envelope, preventing mixed versions during publication. React text rendering prevents HTML execution. Action/message labels describe policy rather than execution.

## ARC-006 — Verification and delivery
Supports: PRN-008, PRN-009; NFR-041, AC-E2E-001 through AC-E2E-005.
Compiler tests cover deterministic mapping and hostile inputs. PostgreSQL integration tests exercise permissions and transaction/version behavior. Frontend tests exercise author interaction and consumer snapshot consistency. Compose smoke tests exercise the acceptance journeys and restart persistence. Each verified task is committed and pushed before the next starts.

## Tradeoffs, assumptions and failure modes
A single backend and direct JDBC avoid unnecessary service layers or distributed locking. Static frontend serving is part of the React service, not a fourth service. Demo identities and local database defaults are intentionally non-production. No rule evaluation, external business action, production identity, pagination or additional workflow is introduced.

Missing SOPs return 404; invalid source is retained only as an author draft; malformed requests and invalid filters return controlled errors. A failed preview is stateless. An invalid publication persists its indicator while preserving the prior publication. The UI retains unsaved source on request failure. No unresolved business question requires expanding scope. CRLF source is accepted for parsing while original source is preserved; plain bullet markers are '- ' or '* '.

Simplicity review: each component is mandated by the runtime or provides a required security, compilation, persistence, rendering or verification boundary; no optional infrastructure remains.
