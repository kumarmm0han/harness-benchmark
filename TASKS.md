# Task tracker

Working branch: `codex-01-sop-demo`. Remote: `origin` (https://github.com/kumarmm0han/harness-benchmark.git). Frozen inputs are never edited. Completion and delivery are distinct; every task must be committed and successfully pushed before starting its successor.

## TASK-001 — Architecture, design and plan
Status: IN_PROGRESS
Implements: ARC-001–006, DES-001–005; PRN-001–009; all requirement IDs indexed in architecture/design.
Depends on: none.
Work: inspect frozen inputs and environment; document architecture, technical decisions, assumptions and ordered work.
Verification: required artifacts exist, all requirement IDs are referenced, input hashes unchanged, branch/remote identified.

## TASK-002 — Safe deterministic compiler
Status: TODO
Implements: DES-002; FR-020, FR-021, FR-030, FR-032, FR-034, NFR-020; PRN-001, PRN-004, PRN-005.
Depends on: TASK-001.
Work: backend build configuration, exact template fixture, parser/compiler and focused hostile-input/semantic tests.
Verification: Maven compiler unit tests pass; valid fixture maps completely, invalid references and independent financial errors are covered.

## TASK-003 — Persistence, authorization and publication API
Status: TODO
Implements: DES-001, DES-003; FR-001, FR-010, FR-042, FR-043, FR-045, FR-050, IR-001, DR-001, DR-003; PRN-003, PRN-004, PRN-006, PRN-007.
Depends on: TASK-002.
Work: migrations, seed, API, identity checks, transaction coordination and PostgreSQL integration tests.
Verification: real PostgreSQL tests pass for permissions, draft persistence, immutable history, concurrent publication and invalid replacement; backend package builds.

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
