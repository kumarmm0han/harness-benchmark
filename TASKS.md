# Task tracker

Working branch: `codex-01-sop-demo`. Remote: `origin` (https://github.com/kumarmm0han/harness-benchmark.git). Frozen inputs are never edited. Completion and delivery are distinct; every task must be committed and successfully pushed before starting its successor.

## TASK-001 — Architecture, design and plan
Status: COMPLETED
Implements: ARC-001–006, DES-001–005; PRN-001–009; all requirement IDs indexed in architecture/design.
Depends on: none.
Work: inspect frozen inputs and environment; document architecture, technical decisions, assumptions and ordered work.
Verification: required artifacts exist, all requirement IDs are referenced, input hashes unchanged, branch/remote identified.
Outcome: first reference assertion caught abbreviated acceptance IDs; explicit mapping added and assertions passed. Frozen inputs unchanged. Java 21/Maven/Node 22/Docker available. Initial artifact commit pushed; this completion correction is committed and pushed before TASK-002.

## TASK-002 — Safe deterministic compiler
Status: COMPLETED
Implements: DES-002; FR-020, FR-021, FR-030, FR-032, FR-034, NFR-020; PRN-001, PRN-004, PRN-005.
Depends on: TASK-001.
Work: backend build configuration, exact template fixture, parser/compiler and focused hostile-input/semantic tests.
Verification: Maven compiler unit tests pass; valid fixture maps completely, invalid references and independent financial errors are covered.
Outcome: `mvn -f backend/pom.xml test -q` passed 8 tests. Initial compile failure used an unavailable SnakeYAML method; removed the unnecessary standard-tag restriction. A boundary-mismatch fixture initially changed both numbers; narrowed it to the boundary and retained the assertion. All tests now pass.

## TASK-003 — Persistence, authorization and publication API
Status: COMPLETED
Implements: DES-001, DES-003; FR-001, FR-010, FR-042, FR-043, FR-045, FR-050, IR-001, DR-001, DR-003; PRN-003, PRN-004, PRN-006, PRN-007.
Depends on: TASK-002.
Work: migrations, seed, API, identity checks, transaction coordination and PostgreSQL integration tests.
Verification: real PostgreSQL tests pass for permissions, draft persistence, immutable history, concurrent publication and invalid replacement; backend package builds.
Outcome: `./scripts/backend-verify.sh -q` exited 0: 8 compiler tests + 4 PostgreSQL integration tests, no failures/skips; executable JAR built. Tests exercised simultaneous publish (200/409), forced post-insert rollback, immutable UPDATE/DELETE rejection, preserved current/history, saved failure markers, stateless preview, seed non-overwrite, CORS and direct permissions. First run exposed Jackson numeric-to-string coercion; explicit coercion rejection fixed it and the full suite passed.

## TASK-004 — Author and consumer UI
Status: TODO
Implements: DES-004; FR-010, FR-034, FR-045, FR-050, FR-052, FR-053, NFR-050; PRN-001, PRN-002, PRN-004.
Depends on: TASK-003.
Work: React/TypeScript editor, preview, draft discovery, publication, filtered list and shared-snapshot human/JSON views; frontend tests.
Verification: frontend tests, TypeScript, lint and production build pass; hostile HTML stays text and author controls respect save state.

## TASK-005 — Compose delivery and final acceptance verification
Status: TODO
Implements: DES-005; NFR-001, NFR-020, NFR-041, AC-E2E-001–005; PRN-007, PRN-008, PRN-009.
Depends on: TASK-004.
Work: three-service Compose, Make commands, smoke/restart journeys, README/API documentation and VERIFICATION.md coverage/evidence.
Verification: make verify, make demo and make smoke pass; three healthy services; restart preserves data; frozen inputs unchanged; complete coverage recorded.
