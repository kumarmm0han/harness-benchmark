package com.sopdemo.web;

import com.sopdemo.config.DemoProperties;
import com.sopdemo.persistence.Draft;
import com.sopdemo.persistence.DraftStore;
import com.sopdemo.web.dto.DraftDtos.DraftDetail;
import com.sopdemo.web.dto.DraftDtos.DraftSummary;
import com.sopdemo.web.dto.DraftDtos.SaveDraftRequest;
import com.sopdemo.web.dto.DraftDtos.SavedDraft;
import com.sopdemo.web.identity.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author-only draft API (FR-010, IR-001). Save is allowed before content is valid (FR-010);
 * saving never changes published state; saving clears the publication-failure indicator.
 */
@RestController
@RequestMapping("/api/v1")
public class DraftController {

    private final DraftStore store;
    private final DemoProperties props;

    public DraftController(DraftStore store, DemoProperties props) {
        this.store = store;
        this.props = props;
    }

    @PutMapping("/drafts/{sopId}")
    public SavedDraft save(@PathVariable String sopId, @RequestBody SaveDraftRequest req, HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        String source = requireSource(req);
        if (tooLarge(source)) {
            throw ApiException.oversized("source exceeds the 65536-byte UTF-8 limit");
        }
        Draft d = store.save(sopId, source);
        return new SavedDraft(d.sopId(), d.revision(), d.source());
    }

    @GetMapping("/drafts")
    public List<DraftSummary> list(HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        return store.list().stream()
                .map(d -> new DraftSummary(d.sopId(), d.revision(), d.publicationFailed(), d.updatedAt()))
                .toList();
    }

    @GetMapping("/drafts/{sopId}")
    public DraftDetail get(@PathVariable String sopId, HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        Draft d = store.find(sopId)
                .orElseThrow(() -> ApiException.notFound("no draft for sop_id " + sopId));
        return new DraftDetail(d.sopId(), d.revision(), d.source(), d.publicationFailed(), d.updatedAt());
    }

    private String requireSource(SaveDraftRequest req) {
        if (req == null || req.source() == null) {
            throw ApiException.badRequest("`source` is required and must be a string");
        }
        return req.source();
    }

    private boolean tooLarge(String source) {
        int max = props.source() == null ? 65536 : props.source().maxBytes();
        return source.getBytes(StandardCharsets.UTF_8).length > max;
    }
}
