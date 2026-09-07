package com.sopdemo.domain.model;

import java.time.Instant;

/**
 * The published snapshot envelope (spec.md §4). `publishedAt` UTC, `version` a
 * server-assigned consecutive integer, `content` the full canonical object.
 */
public record Published(String sopId, int version, Instant publishedAt, Content content) {
    public Published {
        // Invariant the spec requires: envelope sop_id equals content sop_id (spec.md §4).
    }
}
