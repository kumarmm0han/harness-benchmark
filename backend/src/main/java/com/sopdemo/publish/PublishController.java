package com.sopdemo.publish;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sopdemo.api.InvalidRequestException;
import com.sopdemo.identity.Authorize;
import com.sopdemo.publish.PublicationService.Publication;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /api/v1/sops/{sop_id}/publish} (IR-001, author-only).
 * Publishes the saved draft at the requested revision; 200 with the stored
 * canonical snapshot, 409 revision/duplicate conflicts, 422 rejected content,
 * 404 when no draft exists (IR-001).
 */
@RestController
public class PublishController {

    private static final Pattern SOP_ID = Pattern.compile("[A-Z][A-Z0-9-]{0,63}");

    private final PublicationService service;

    public PublishController(PublicationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/sops/{sopId}/publish")
    public Map<String, Object> publish(@PathVariable String sopId,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest request) {
        Authorize.requireAuthor(request);
        if (!SOP_ID.matcher(sopId).matches()) {
            throw new InvalidRequestException("sop_id must match [A-Z][A-Z0-9-]{0,63}.");
        }
        if (body == null) {
            throw new InvalidRequestException("Request body is required.");
        }
        Object requested = body.get("revision");
        if (!(requested instanceof Number n)) {
            throw new InvalidRequestException("Field 'revision' must be the numeric draft revision.");
        }
        long revision = n.longValue();
        if (revision <= 0) {
            throw new InvalidRequestException("Field 'revision' must be a positive integer.");
        }
        Publication publication = service.publish(sopId, revision);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sop_id", publication.sopId());
        out.put("version", publication.version());
        // Server-assigned UTC publish time (spec.md §4 envelope).
        out.put("published_at", java.time.OffsetDateTime.ofInstant(publication.publishedAt(), java.time.ZoneOffset.UTC));
        out.put("content", publication.content()); // stored canonical snapshot (FR-042)
        return out;
    }
}
