package com.sopdemo.web;

import com.sopdemo.config.DemoProperties;
import com.sopdemo.domain.PolicyResult;
import com.sopdemo.domain.model.Published;
import com.sopdemo.domain.validate.ValidationService;
import com.sopdemo.persistence.PublicationService;
import com.sopdemo.web.dto.PublishDtos.PublishRequest;
import com.sopdemo.web.dto.PublishDtos.ValidateRequest;
import com.sopdemo.web.identity.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author-only validate + publish (FR-034, FR-042, IR-001, DES-013). {@code /validate} always
 * returns 200 (even for content issues; malformed envelope → 400; oversized → 413). Publish
 * returns the stored canonical snapshot (200); stale/duplicate revision → 409; rejected
 * content → 422; no such draft → 404.
 */
@RestController
@RequestMapping("/api/v1")
public class PolicyAuthorController {

    private final ValidationService validation;
    private final PublicationService publicationService;
    private final DemoProperties props;

    public PolicyAuthorController(
            ValidationService validation, PublicationService publicationService, DemoProperties props) {
        this.validation = validation;
        this.publicationService = publicationService;
        this.props = props;
    }

    @PostMapping("/validate")
    public PolicyResult validate(@RequestBody ValidateRequest req, HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        String source = requireSource(req);
        if (tooLarge(source)) {
            throw ApiException.oversized("source exceeds the 65536-byte UTF-8 limit");
        }
        return validation.validate(source);
    }

    @PostMapping("/sops/{sopId}/publish")
    public Published publish(
            @PathVariable String sopId, @RequestBody PublishRequest req, HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        if (req == null || req.revision() == null || req.revision() < 1) {
            throw ApiException.badRequest("`revision` is required and must be a positive integer");
        }
        return publicationService.publish(sopId, req.revision());
    }

    private String requireSource(ValidateRequest req) {
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
