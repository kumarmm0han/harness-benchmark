# SOP System — Demo Engineering Principles

This baseline supports the trimmed scope in `REQUIREMENTS.md` and the format in `spec.md`. It replaces the previous MVP guardrails and evaluation gates. During implementation these inputs are frozen; keep architecture, design, tasks, and code traceable to their IDs.

## PRN-001 — One canonical source

Business users author Markdown. Successful publication produces an immutable canonical JSON snapshot stored with its source. Human and AI views consume the same snapshot. Never maintain separately editable human and machine policy representations.

## PRN-002 — Complete the small journey

Deliver template authoring, safe validation, immediate publication, and both consumer views. Follow the explicit deferrals in `REQUIREMENTS.md`. Do not add review workflows, scheduled activation, an expression engine, or production infrastructure. Implement the supported low/medium, assist-only content model; reject unsupported policy values.

## PRN-003 — Three-service local runtime

Use React/TypeScript, Java 21/Spring Boot, and PostgreSQL under Docker Compose. Keep parsing, validation, publication, and querying inside one backend deployment. Use framework capabilities and dependencies with a concrete demo purpose; avoid speculative interfaces and layers. No queues, caches, workflow engines, cloud services, or external actions.

## PRN-004 — Backend authority and untrusted content

The backend owns identity checks, validation, compilation, and publication. UI controls cannot replace backend enforcement. Treat Markdown/YAML and all content as data; never execute rule conditions, action descriptions, templates, or scripts. Use safe parsing with the specified limits and render strings as text. Do not expose secrets or implementation internals in errors.

## PRN-005 — Deterministic, readable validation

Implement only the format in `spec.md`. Reject unknown fields and ambiguous input instead of silently dropping policy content. Return stable structural/semantic issues with useful paths. Identical source yields identical content and issues; publication metadata is the only variable addition. Keep financial limit and escalation checks centralized and independently testable.

## PRN-006 — Atomic publication and preserved history

Validate the exact saved revision before publication. Use PostgreSQL transactions and constraints to assign unique versions, preserve immutable snapshots, and update one current pointer. Invalid replacements and failed transactions cannot replace current content. Persist the failed-publication indicator as specified. Use ordinary database coordination, not distributed locking infrastructure.

## PRN-007 — Reproducible local delivery

One Compose command starts the three healthy services with safe local defaults and deterministic seed data. Use migrations and a named PostgreSQL volume. Seed initialization must preserve user edits. Document startup, verification, shutdown, and explicit data deletion. Integration tests for database behavior use PostgreSQL.

## PRN-008 — Evidence proportional to scope

Follow NFR-041 and the five acceptance journeys in `REQUIREMENTS.md`. Test meaningful behavior, including invalid input, permissions, version integrity, and consumer consistency. Do not delete legitimate failing tests or weaken checks to pass. Record actual commands, outcomes, assumptions, and limitations. Generated OpenAPI, exhaustive endpoint matrices, performance targets, and a separate public evaluation suite are outside this baseline.

## PRN-009 — Traceable implementation

Reference requirements and principles in architecture/design decisions and tasks. Keep `TASKS.md` current, mark each verified task completed during implementation, and commit its code/tests/artifact updates together using its task ID. Push to the run's working branch after each completed task and confirm success before starting the next, as required by the harness skill. Report delivery failures honestly and never force-push to bypass them. Correct lower-level artifacts when implementation changes the design.
