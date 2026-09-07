package com.sopdemo.drafts;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sopdemo.api.InvalidRequestException;
import com.sopdemo.content.SourceLimits;
import com.sopdemo.identity.Authorize;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Draft endpoints (IR-001, author-only):
 * <ul>
 *   <li>{@code PUT /api/v1/drafts/{sop_id}} - save source; revision increments (FR-010)</li>
 *   <li>{@code GET /api/v1/drafts} - author draft summaries (FR-050)</li>
 *   <li>{@code GET /api/v1/drafts/{sop_id}} - saved source + revision + failure indicator (FR-045)</li>
 * </ul>
 * Saving is allowed before content is valid (IR-001). Oversized sources are 413 (FR-020).
 */
@RestController
public class DraftController {

    /** IR-001: sop_id charset ([A-Z] followed by up to 63 of [A-Z0-9-]). */
    private static final java.util.regex.Pattern SOP_ID = java.util.regex.Pattern.compile("[A-Z][A-Z0-9-]{0,63}");

    public record DraftPutResponse(String sopId, long revision, String source) {
    }

    public record DraftSummary(String sopId, long revision, boolean publishFailed, OffsetDateTime savedAt) {
    }

    public record DraftDetail(String sopId, String source, long revision, boolean publishFailed,
                              OffsetDateTime savedAt) {
    }

    private final DraftStore store;

    public DraftController(DraftStore store) {
        this.store = store;
    }

    @PutMapping("/api/v1/drafts/{sopId}")
    public DraftPutResponse save(@PathVariable String sopId,
                                 @RequestBody(required = false) java.util.Map<String, Object> body,
                                 HttpServletRequest request) {
        Authorize.requireAuthor(request);
        if (!SOP_ID.matcher(sopId).matches()) {
            throw new InvalidRequestException("sop_id must match [A-Z][A-Z0-9-]{0,63}.");
        }
        if (body == null) {
            throw new InvalidRequestException("Request body is required.");
        }
        Object source = body.get("source");
        if (!(source instanceof String text)) {
            throw new InvalidRequestException("Field 'source' must be a string.");
        }
        SourceLimits.check(text, SourceLimits.MAX_SOURCE_BYTES); // 413 via SourceTooLargeException (FR-020)
        long revision = store.save(sopId, text);
        return new DraftPutResponse(sopId, revision, text);
    }

    @GetMapping("/api/v1/drafts")
    public java.util.Map<String, List<DraftSummary>> list(HttpServletRequest request) {
        Authorize.requireAuthor(request);
        List<DraftSummary> drafts = store.list().stream()
                .map(d -> new DraftSummary(d.sopId(), d.revision(), d.publishFailed(), d.savedAt()))
                .toList();
        return java.util.Map.of("drafts", drafts);
    }

    @GetMapping("/api/v1/drafts/{sopId}")
    public DraftDetail get(@PathVariable String sopId, HttpServletRequest request) {
        Authorize.requireAuthor(request);
        return store.find(sopId)
                .map(d -> new DraftDetail(d.sopId(), d.source(), d.revision(), d.publishFailed(), d.savedAt()))
                .orElseThrow(() -> new com.sopdemo.api.SopException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "draft-not-found",
                        "No saved draft exists for '" + sopId + "'."));
    }
}
