package com.sopdemo.persistence;

import java.time.Instant;

/**
 * One immutable published snapshot (DR-001, FR-043). {@code snapshot} is the canonical
 * {@code Content} serialized to JSON (stored in the JSONB column); {@code source} is the
 * saved author text that produced it, kept alongside (FR-021, spec.md §4).
 */
public record Publication(
        String sopId,
        int version,
        long draftRevision,
        String source,
        String snapshot,
        Instant publishedAt) {}
