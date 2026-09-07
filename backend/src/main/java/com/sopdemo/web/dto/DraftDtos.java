package com.sopdemo.web.dto;

import java.time.Instant;

/** Request/response DTOs for the draft API (IR-001). */
public final class DraftDtos {
    private DraftDtos() {}

    public record SaveDraftRequest(String source) {}

    public record SavedDraft(String sopId, long revision, String source) {}

    public record DraftDetail(String sopId, long revision, String source, boolean publicationFailed, Instant updatedAt) {}

    public record DraftSummary(String sopId, long revision, boolean publicationFailed, Instant updatedAt) {}
}
