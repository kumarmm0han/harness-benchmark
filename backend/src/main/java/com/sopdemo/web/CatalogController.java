package com.sopdemo.web;

import com.sopdemo.domain.model.Published;
import com.sopdemo.persistence.PublicationService;
import com.sopdemo.web.dto.CatalogDtos.SopSummary;
import com.sopdemo.web.identity.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Published-catalog reads (FR-050/052/053, IR-001, DES-013). List + detail are open to both
 * identities; the immutable version history is author-only (consumer on it → 403). Invalid
 * filter values → 400; absent resources → 404.
 */
@RestController
@RequestMapping("/api/v1/sops")
public class CatalogController {

    private static final Set<String> DOMAINS = Set.of("Billing", "Support");
    private static final Set<String> RISKS = Set.of("low", "medium");

    private final PublicationService publicationService;

    public CatalogController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping
    public List<SopSummary> list(
            @RequestParam(required = false) String domain, @RequestParam(required = false) String risk) {
        requireValidFilter("domain", domain, DOMAINS);
        requireValidFilter("risk", risk, RISKS);
        return publicationService.list(domain, risk);
    }

    @GetMapping("/{sopId}")
    public Published detail(@PathVariable String sopId) {
        Published p = publicationService.currentPublished(sopId);
        if (p == null) {
            throw ApiException.notFound("no published SOP for sop_id " + sopId);
        }
        return p;
    }

    @GetMapping("/{sopId}/versions/{version}")
    public Published version(
            @PathVariable String sopId, @PathVariable Integer version, HttpServletRequest request) {
        AuthContext.requireAuthor(request);
        if (version == null || version < 1) {
            throw ApiException.badRequest("version must be a positive integer");
        }
        Published p = publicationService.publishedVersion(sopId, version);
        if (p == null) {
            throw ApiException.notFound("version " + version + " not found for sop_id " + sopId);
        }
        return p;
    }

    private static void requireValidFilter(String name, String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw ApiException.badRequest("invalid " + name + " filter: " + value);
        }
    }
}
