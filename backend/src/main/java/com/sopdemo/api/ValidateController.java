package com.sopdemo.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sopdemo.content.ContentEngine;
import com.sopdemo.content.ContentEngine.ContentAnalysis;
import com.sopdemo.content.Issue;
import com.sopdemo.identity.Authorize;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /api/v1/validate} (IR-001, author-only): dry-run validation.
 *
 * <p>Contract: always 200 for content results — {@code {valid, issues, content}} where
 * content is null when invalid and issues is empty when valid (FR-034). This call is
 * read-only: it never mutates drafts, versions, or the current pointer (FR-034).
 * Malformed envelopes are 400; oversized sources are 413 (FR-020).</p>
 */
@RestController
public class ValidateController {

    /** Response shape fixed by IR-001. {@code content} is null when invalid. */
    public record ValidateResponse(boolean valid, List<Issue> issues, Map<String, Object> content) {
    }

    private final ContentEngine engine;

    public ValidateController(ContentEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/api/v1/validate")
    public ValidateResponse validate(@RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        Authorize.requireAuthor(request);
        if (body == null) {
            throw new InvalidRequestException("Request body is required.");
        }
        Object source = body.get("source");
        if (!(source instanceof String text)) {
            throw new InvalidRequestException("Field 'source' must be a string containing the SOP source.");
        }
        ContentAnalysis analysis = engine.analyze(text);
        return new ValidateResponse(analysis.valid(), analysis.issues(), analysis.content());
    }
}
