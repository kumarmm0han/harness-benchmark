package com.sop.api;

import com.sop.domain.Contract;
import com.sop.domain.Envelope;
import com.sop.dto.SopSummary;
import com.sop.service.PublishService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consumer-allowed endpoints (IR-001): list + detail + historical.
 * List/detail are readable by both identities; historical is author-only (DES-007).
 */
@RestController
@RequestMapping("/api/v1/sops")
public class PublicApi {

  private final PublishService publish;

  public PublicApi(PublishService publish) {
    this.publish = publish;
  }

  @GetMapping
  public List<SopSummary> list(@RequestParam(value = "domain", required = false) String domain,
                               @RequestParam(value = "risk", required = false) String risk) {
    // The filter values must be from the fixed set (IR-001 400 INVALID_FILTER).
    if (domain != null && !Contract.DOMAINS.contains(domain)) {
      throw new ApiException(ErrorCode.INVALID_FILTER,
          "domain must be one of Billing, Support");
    }
    if (risk != null && !Contract.RISKS.contains(risk)) {
      throw new ApiException(ErrorCode.INVALID_FILTER,
          "risk must be one of low, medium");
    }
    return publish.list(domain, risk);
  }

  @GetMapping("/{sopId}")
  public Envelope get(@PathVariable("sopId") String sopId) {
    return publish.getCurrent(sopId);
  }

  @GetMapping("/{sopId}/versions/{version}")
  public Envelope getVersion(@PathVariable("sopId") String sopId,
                             @PathVariable("version") int version,
                             HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    return publish.getVersion(sopId, version);
  }
}
