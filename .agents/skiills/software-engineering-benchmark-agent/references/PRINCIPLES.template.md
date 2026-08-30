# PRINCIPLES.md

## PRN-001 — Simplicity first
Prefer the simplest implementation that fully satisfies the requirements.

## PRN-002 — No speculative abstractions
Do not add extension points, layers, or infrastructure for hypothetical future needs.

## PRN-003 — Centralize business rules
Business rules should not be duplicated across controllers, UI components, or persistence code.

## PRN-004 — Testable domain behavior
Important business behavior should be testable independently of HTTP/UI concerns.

## PRN-005 — Clear API errors
Validation and business-rule failures should be represented consistently.

## PRN-006 — Minimal dependency surface
Do not introduce new libraries when the existing stack can reasonably solve the problem.

## PRN-007 — Traceability
Architecture, design, tasks, implementation, and tests should remain traceable to requirements.

## PRN-008 — Preserve existing conventions
Follow the starter repository's conventions unless a requirement justifies changing them.
