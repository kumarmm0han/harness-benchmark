package com.sopdemo.persistence;

import java.time.Instant;

/** One editable draft per {@code sop_id} (FR-010, ARC-005). */
public record Draft(
        String sopId,
        String source,
        long revision,
        boolean publicationFailed,
        Instant updatedAt) {}
