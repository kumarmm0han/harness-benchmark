package com.sop.service;

import com.sop.api.ApiException;
import com.sop.api.ErrorCode;
import com.sop.domain.Contract;
import com.sop.domain.Draft;
import com.sop.domain.DraftRepository;
import com.sop.domain.Envelope;
import com.sop.domain.Publication;
import com.sop.domain.PublicationRepository;
import com.sop.domain.SopCurrent;
import com.sop.domain.SopCurrentRepository;
import com.sop.dto.Issue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Atomic immediate publication (DES-008b, FR-042 / FR-043 / FR-045).
 *
 * <p>Single transaction:
 * <ol>
 *   <li>fetch the saved draft (404 if absent).</li>
 *   <li>compare the requested revision with the stored revision (409 STALE).</li>
 *   <li>re-validate the EXACT saved source (never a client-supplied one).</li>
 *   <li>if invalid: set the failure indicator on the draft, persist, then 422
 *       with the issue list (FR-045 / FR-034).</li>
 *   <li>advisory lock keyed by sop_id (PRN-006, DES-002).</li>
 *   <li>next version = max+1 (read under the lock).</li>
 *   <li>insert the immutable (source, content, envelope, version) row.</li>
 *   <li>upsert the sop_current pointer.</li>
 *   <li>clear the failure indicator.</li>
 * </ol>
 *
 * <p>Any exception rolls all writes back; the unique (sop_id, draft_revision)
 * constraint is the final backstop for duplicate publishes (409).
 */
@Service
public class PublishService {

  private static final Logger log = LoggerFactory.getLogger(PublishService.class);

  private final DraftRepository drafts;
  private final PublicationRepository publications;
  private final SopCurrentRepository currents;
  private final ValidationService validation;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public PublishService(DraftRepository drafts,
                        PublicationRepository publications,
                        SopCurrentRepository currents,
                        ValidationService validation,
                        JdbcTemplate jdbc,
                        ObjectMapper mapper) {
    this.drafts = drafts;
    this.publications = publications;
    this.currents = currents;
    this.validation = validation;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  public Envelope publish(String sopId, Long requestedRevision) {
    if (requestedRevision == null) {
      throw new ApiException(ErrorCode.MALFORMED, "`revision` is required to publish");
    }

    Draft draft = drafts.findById(sopId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
            "no saved draft exists for sop_id `" + sopId + "`"));

    if (draft.revision() != requestedRevision) {
      throw new ApiException(ErrorCode.STALE_REVISION,
          "draft revision " + requestedRevision
              + " is stale (the saved revision is " + draft.revision() + ")");
    }

    // Re-validate the EXACT saved source (IR-001 "The backend revalidates that
    // source"; PRN-004 backend authority).
    ValidationService.Outcome outcome = validation.validate(draft.source());
    if (!outcome.valid()) {
      // FR-045: persist the failure indicator, leave current unchanged.
      draft.setPublishFailedAt(Instant.now());
      drafts.save(draft);
      throw new ApiException(ErrorCode.VALIDATION_FAILED,
          "saved draft fails validation; current publication is unchanged",
          outcome.issues());
    }

    Envelope envelope = new Envelope(sopId, null, null, outcome.content());

    // Concurrency: serialize publishers of the same SOP (DES-002, PRN-006).
    acquireAdvisoryLock(sopId);

    int nextVersion = publications.nextVersionFor(sopId);
    Instant publishedAt = Instant.now();
    envelope = new Envelope(sopId, nextVersion, publishedAt.toString(), outcome.content());

    // The envelope sop_id must equal the draft path sop_id (FR-021 / IR-001).
    if (!sopId.equals(envelope.sopId())) {
      draft.setPublishFailedAt(Instant.now());
      drafts.save(draft);
      throw new ApiException(ErrorCode.VALIDATION_FAILED,
          "content.sop_id does not match the draft sop_id",
          List.of(Issue.semantic(com.sop.domain.Contract.C_SOP_ID_MISMATCH,
              "content.sop_id must equal the draft sop_id", "content.sop_id")));
    }

    Publication publication = new Publication();
    publication.setSopId(sopId);
    publication.setVersion(nextVersion);
    publication.setDraftRevision(draft.revision());
    publication.setSource(draft.source());
    publication.setContentJson(toJson(outcome.content()));
    publication.setEnvelopeJson(toJson(envelope));
    publication.setPublishedAt(publishedAt);
    publications.save(publication);

    SopCurrent current = currents.findById(sopId).orElseGet(SopCurrent::new);
    current.setSopId(sopId);
    current.setVersion(nextVersion);
    current.setPublishedAt(publishedAt);
    currents.save(current);

    // Successful publish clears the failure indicator (FR-045).
    if (draft.publishFailedAt() != null) {
      draft.setPublishFailedAt(null);
      drafts.save(draft);
    }

    log.info("published sop_id={} version={}", sopId, nextVersion);
    return envelope;
  }

  @Transactional(readOnly = true)
  public Envelope getCurrent(String sopId) {
    SopCurrent current = currents.findById(sopId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
            "sop_id `" + sopId + "` has no current published version"));
    Publication p = publications.findBySopIdAndVersion(sopId, current.version())
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
            "current snapshot for `" + sopId + "` is missing"));
    return fromEnvelopeJson(p.envelopeJson());
  }

  @Transactional(readOnly = true)
  public Envelope getVersion(String sopId, int version) {
    if (version < 1) {
      throw new ApiException(ErrorCode.MALFORMED, "`version` must be a positive integer");
    }
    Publication p = publications.findBySopIdAndVersion(sopId, version)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
            "sop_id `" + sopId + "` has no version " + version));
    return fromEnvelopeJson(p.envelopeJson());
  }

  /**
   * List of current published SOP summaries (FR-050).
   * Filters: AND of `domain`/`risk` (either/both optional).
   * Sort: sop_id ascending. Empty result returns `[]` (UI shows empty state).
   */
  @Transactional(readOnly = true)
  public List<com.sop.dto.SopSummary> list(String domain, String risk) {
    return currents.findAll().stream()
        .sorted(java.util.Comparator.comparing(SopCurrent::sopId))
        .map(current -> {
          Publication p = publications.findBySopIdAndVersion(current.sopId(), current.version())
              .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL,
                  "current snapshot is missing for `" + current.sopId() + "`"));
          return toSummary(p, current);
        })
        .filter(summary -> matchesFilter(summary, domain, risk))
        .toList();
  }

  private static boolean matchesFilter(com.sop.dto.SopSummary s, String domain, String risk) {
    boolean domainOk = domain == null || domain.equals(s.domain());
    boolean riskOk = risk == null || risk.equals(s.risk());
    return domainOk && riskOk;
  }

  private com.sop.dto.SopSummary toSummary(Publication p, SopCurrent current) {
    Envelope env = fromEnvelopeJson(p.envelopeJson());
    String title = env.content() == null ? null : env.content().title();
    String domain = env.content() == null ? null : env.content().domain();
    String risk = env.content() == null ? null : env.content().riskLevel();
    return new com.sop.dto.SopSummary(env.sopId(), title, current.version(), domain, risk);
  }

  /**
   * Acquire a transaction-scoped advisory lock keyed by the sop_id hash
   * (DES-002, PRN-006 — "ordinary database coordination, not distributed locking").
   *
   * <p>{@code hashtext(sop_id)} is a stable 32-bit hash. Postgres auto-widens
   * int4 to the bigint that {@code pg_advisory_xact_lock} requires. The lock
   * is released automatically when the surrounding transaction ends.
   *
   * <p>Two publishers of the same sop_id are serialized; different sop_ids never
   * block each other. Duplicate (sop_id, draft_revision) is the SQL constraint
   * backstop that turns a race into a 409.
   */
  private void acquireAdvisoryLock(String sopId) {
    // `update` here is the "execute and ignore results" overload — it happily
    // runs a SELECT-returning function and is not affected by the JDBC "no
    // results" restriction on `queryForObject`.
    jdbc.update("SELECT pg_advisory_xact_lock(hashtext(?))", sopId);
  }

  private String toJson(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (Exception e) {
      throw new RuntimeException("serialization failed", e);
    }
  }

  private Envelope fromEnvelopeJson(String json) {
    if (json == null) throw new ApiException(ErrorCode.INTERNAL, "stored envelope is missing");
    try {
      return mapper.readValue(json, Envelope.class);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.INTERNAL, "stored envelope could not be deserialized");
    }
  }
}
