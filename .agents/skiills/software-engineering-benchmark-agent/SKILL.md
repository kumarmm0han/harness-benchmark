---
name: software-engineering-benchmark
description: Execute a controlled software-engineering benchmark from fixed PRINCIPLES.md and REQUIREMENTS.md through architecture, technical design, task planning, implementation, testing, and verification. Use when comparing coding harnesses with the same model, repository, requirements, and engineering guardrails. Preserve requirement traceability and avoid speculative architecture or unnecessary abstractions.
---

# Software Engineering Benchmark Agent

## Purpose

Compare coding harnesses while keeping the problem definition and model constant.

Each harness receives the same:
- `PRINCIPLES.md`
- `REQUIREMENTS.md`
- starter repository
- model and inference settings

Each harness independently produces:
1. `ARCHITECTURE.md`
2. `TECHNICAL_DESIGN.md`
3. `TASKS.md`
4. implementation
5. tests
6. `VERIFICATION.md`

The goal is to measure how the harness affects architecture, planning, task decomposition, implementation, simplicity, testing, debugging, recovery, and requirement-to-code drift.

## Core experimental rule

**Do not change the problem definition.**

`PRINCIPLES.md` and `REQUIREMENTS.md` are frozen inputs.

Do not silently reinterpret requirements, add unrequested features, weaken requirements, invent missing business rules, or introduce infrastructure without a clear need.

When a requirement is ambiguous, record the ambiguity and choose the smallest reasonable assumption required to proceed.

## Core engineering rule

**Prefer the simplest design that satisfies the requirements and principles.**

Patterns and abstractions are tools, not goals.

Avoid speculative layers, unnecessary interfaces, infrastructure for hypothetical future needs, and abstractions justified only by possible future extensibility.

## Required lifecycle

```text
PRINCIPLES.md + REQUIREMENTS.md
            ↓
       ARCHITECTURE.md
            ↓
     TECHNICAL_DESIGN.md
            ↓
          TASKS.md
            ↓
      IMPLEMENTATION
            ↓
          TESTING
            ↓
        VERIFICATION
```

## Stage 0 — Read and index inputs

Read `PRINCIPLES.md`, `REQUIREMENTS.md`, the starter repository, build configuration, existing tests, and conventions before editing source code.

Preserve existing requirement IDs. If none exist, assign stable IDs such as `REQ-001`, `SEC-001`, `PERF-001`, and `PRN-001`.

## Stage 1 — Architecture

Produce `ARCHITECTURE.md` covering:
- system context
- major components and responsibilities
- important boundaries and interfaces
- key data flows
- external dependencies
- security boundaries
- failure modes
- deployment/runtime assumptions
- major tradeoffs
- unresolved questions
- requirement traceability

Keep this stage high-level. Do not include low-level method signatures, ORM mappings, or implementation tasks.

Use architecture IDs such as `ARC-001` and reference the requirements/principles they support.

Example:

```markdown
### ARC-004 — Centralize approval state transitions
Supports: REQ-007, REQ-009, PRN-003
Rationale: approval rules should not be duplicated across controllers or UI code.
```

Before finalizing, ask whether each component and boundary is actually necessary and whether the design can be simpler.

## Stage 2 — Technical design

Produce `TECHNICAL_DESIGN.md` translating the architecture into an implementable design.

When applicable, cover backend package/module structure, domain model, services, REST APIs, DTOs, persistence, validation, error handling, authorization, transactions, concurrency, logging, frontend component structure, routing, forms, API-client organization, state management, error/loading states, role-aware UI behavior, configuration, migrations, and test strategy.

Use design IDs such as `DES-001`. Each important decision must reference the relevant `ARC-*`, `REQ-*`, and `PRN-*` items.

### Pattern-selection rule

Use a pattern only when it addresses a concrete design problem. When introducing a notable abstraction, document the problem, chosen approach, why it is simpler or better than alternatives, and the requirements it supports.

## Stage 3 — Task planning

Produce `TASKS.md`.

Each task should include:
- task ID such as `TASK-023`
- requirements/design IDs implemented
- dependencies
- concrete work
- verification criteria

Example:

```markdown
### TASK-023 — Implement approval transition rules
Implements: DES-011, REQ-007, REQ-009
Depends on: TASK-018
Work:
- add transition validation
- enforce approver rules
- add service/domain tests
Verification:
- valid and invalid transitions are covered
- API rejects unauthorized approval
```

Avoid vague tasks, unrelated work bundled together, meaningless microtasks, and tasks with no verification.

## Stage 4 — Implementation

Implement according to this priority:
1. `PRINCIPLES.md`
2. `REQUIREMENTS.md`
3. `ARCHITECTURE.md`
4. `TECHNICAL_DESIGN.md`
5. `TASKS.md`

If a lower-level artifact conflicts with a higher-level source, the higher-level source wins. Update the lower-level artifact and record the correction.

Inspect relevant existing code before editing. Keep changes scoped. Preserve repository conventions when reasonable. Prefer clear code over clever code. Centralize business rules, avoid duplication, and add tests with behavior.

### Design drift

If implementation requires a material design change, update `TECHNICAL_DESIGN.md` and affected `TASKS.md`, record the reason, then continue. Do not quietly diverge from the design.

## Stage 5 — Testing and recovery

Run the repository's available build, unit, integration, frontend, type-check, formatting, and static-analysis commands as applicable.

When a check fails:
1. inspect the failure
2. identify the likely root cause
3. make the smallest justified correction
4. rerun the relevant check
5. rerun broader verification

Do not delete failing tests, weaken assertions, disable validation/security, or replace a requirement with a workaround merely to get green.

## Stage 6 — Final verification

Produce `VERIFICATION.md` containing:

### Requirement coverage

| Requirement | Architecture | Design | Tasks | Code | Tests | Status |
|---|---|---|---|---|---|---|
| REQ-007 | ARC-004 | DES-011 | TASK-023 | implemented | covered | PASS |

Statuses: `PASS`, `PARTIAL`, `FAIL`, `NOT_VERIFIED`.

### Principle adherence
For each important `PRN-*`, explain how it was satisfied or identify a deviation.

### Test/build results
Record actual commands and outcomes.

### Design-to-code consistency
Report material deviations and their reasons.

### Known limitations
List only real known limitations.

### Assumptions
List every material assumption made due to ambiguity.

## Harness-neutrality rules

Do not assume a specific harness API, agent name, memory system, planning mode, or subagent feature. A harness may use its native mechanisms internally, but the observable artifacts and lifecycle remain the same.

## Benchmark fairness

The benchmark controller should keep constant where practical:
- model and model version
- quantization
- inference server
- context window
- sampling parameters
- reasoning/thinking configuration
- starter repository commit
- requirements
- principles
- tool permissions
- runtime versions
- database/runtime dependencies
- time budget

## Required output artifacts

At completion, the repository must contain:
- `ARCHITECTURE.md`
- `TECHNICAL_DESIGN.md`
- `TASKS.md`
- `VERIFICATION.md`
- implementation
- tests

## Quality priorities

1. correctness
2. requirement adherence
3. principle/guardrail adherence
4. simplicity
5. maintainability
6. design quality
7. test quality
8. efficiency

Do not trade correctness for elegance.
