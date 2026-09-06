package com.sop.service;

import com.sop.api.ApiException;
import com.sop.api.ErrorCode;
import com.sop.domain.Draft;
import com.sop.domain.DraftRepository;
import com.sop.dto.DraftDto;
import com.sop.dto.DraftSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * One editable draft per sop_id (DES-008a, FR-010).
 * - save: enforces 64 KiB, upserts, bumps revision, clears the failure indicator.
 * - list: all drafts (single author identity in the demo) with the indicator.
 * Saving never touches published content (FR-010).
 */
@Service
public class DraftService {

  private static final int MAX_BYTES = com.sop.domain.Contract.SOURCE_BYTES_MAX;

  private final DraftRepository repo;

  public DraftService(DraftRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public DraftDto save(String sopId, String source) {
    if (source == null) {
      throw new ApiException(ErrorCode.MALFORMED, "draft `source` is required");
    }
    if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new ApiException(ErrorCode.SOURCE_TOO_LARGE,
          "source exceeds " + MAX_BYTES + " UTF-8 bytes");
    }
    boolean isNew = repo.findById(sopId).isEmpty();
    Draft d;
    if (isNew) {
      d = new Draft();
      d.setSopId(sopId);
      d.setRevision(1);
      d.setCreatedAt(Instant.now());
    } else {
      d = repo.findById(sopId).orElseThrow();
      d.setRevision(d.revision() + 1);
    }
    d.setSource(source);
    d.setUpdatedAt(Instant.now());
    d.setPublishFailedAt(null); // clear the failure indicator on save (FR-045)
    repo.save(d);
    return toDto(d);
  }

  @Transactional(readOnly = true)
  public DraftDto get(String sopId) {
    Draft d = repo.findById(sopId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "no draft for sop_id `" + sopId + "`"));
    return toDto(d);
  }

  @Transactional(readOnly = true)
  public List<DraftSummary> list() {
    return repo.findAllByOrderBySopIdAsc().stream()
        .map(d -> new DraftSummary(d.sopId(), d.revision(),
            d.publishFailedAt() != null))
        .toList();
  }

  private static DraftDto toDto(Draft d) {
    return new DraftDto(d.sopId(), d.revision(), d.source(),
        d.publishFailedAt() != null, d.updatedAt());
  }
}
