package com.sop.api;

import com.sop.domain.Envelope;
import com.sop.dto.DraftDto;
import com.sop.dto.DraftRequest;
import com.sop.dto.DraftSummary;
import com.sop.dto.PublishRequest;
import com.sop.dto.SopSummary;
import com.sop.dto.ValidateRequest;
import com.sop.dto.ValidateResponse;
import com.sop.service.DraftService;
import com.sop.service.PublishService;
import com.sop.service.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Author-only endpoints (IR-001). Role enforced server-side via
 * {@link Role#requireAuthor} (PRN-004: "UI controls cannot replace backend
 * enforcement").
 */
@RestController
@RequestMapping("/api/v1")
public class AuthorApi {

  private final ValidationService validation;
  private final DraftService drafts;
  private final PublishService publish;

  public AuthorApi(ValidationService validation, DraftService drafts, PublishService publish) {
    this.validation = validation;
    this.drafts = drafts;
    this.publish = publish;
  }

  @PostMapping("/validate")
  public ValidateResponse validate(@RequestBody(required = false) ValidateRequest req,
                                   HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    if (req == null || req.source() == null || req.source().isBlank()) {
      throw new ApiException(ErrorCode.MALFORMED, "`source` is required");
    }
    ValidationService.Outcome outcome = validation.validate(req.source());
    return new ValidateResponse(outcome.valid(), outcome.issues(), outcome.content());
  }

  @PutMapping("/drafts/{sopId}")
  public DraftDto saveDraft(@PathVariable("sopId") String sopId,
                            @RequestBody(required = false) DraftRequest req,
                            HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    if (req == null || req.source() == null) {
      throw new ApiException(ErrorCode.MALFORMED, "`source` is required");
    }
    return drafts.save(sopId, req.source());
  }

  @GetMapping("/drafts")
  public List<DraftSummary> listDrafts(HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    return drafts.list();
  }

  @GetMapping("/drafts/{sopId}")
  public DraftDto getDraft(@PathVariable("sopId") String sopId, HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    return drafts.get(sopId);
  }

  @PostMapping("/sops/{sopId}/publish")
  public Envelope publish(@PathVariable("sopId") String sopId,
                          @RequestBody(required = false) PublishRequest req,
                          HttpServletRequest httpRequest) {
    Role.requireAuthor(httpRequest);
    if (req == null || req.revision() == null) {
      throw new ApiException(ErrorCode.MALFORMED, "`revision` is required to publish");
    }
    return publish.publish(sopId, req.revision());
  }
}
