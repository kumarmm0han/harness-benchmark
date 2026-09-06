package com.sop.service;

import com.sop.api.ApiException;
import com.sop.api.ErrorCode;
import com.sop.domain.Contract;
import com.sop.domain.Content;
import com.sop.domain.Draft;
import com.sop.domain.DraftRepository;
import com.sop.domain.Envelope;
import com.sop.domain.Publication;
import com.sop.domain.PublicationRepository;
import com.sop.domain.SopCurrent;
import com.sop.domain.SopCurrentRepository;
import com.sop.dto.Issue;
import com.sop.service.ValidationService.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Atomic immediate publication (DES-008b, FR-042 / FR-043 / FR-045).
 *
 * <p>Publishing is split across short transactions so that the FR-045 failure
 * indicator durably survives the 422 response, while every publication write
 * stays atomic:
 *
 * <ol>
 *   <li>Non-transactional orchestration in {@link #publish}: fetch the saved
 *       draft (404 if absent), stale-revision check (409), and re-validation of
 *       the EXACT saved source (never a client-supplied one) in that order.</li>
 *   <li>Failed publish: {@link #recordPublishFailure(String)} commits
 *       {@code publish_failed_at = now()} in its own transaction, then the
 *       orchestrator returns 422 with the issue list. No publication write was
 *       attempted, so the current pointer is untouched (FR-042 / FR-045).</li>
 *   <li>Successful publish: {@link #commitPublish(String, Long, Content)}
 *       performs everything in a single transaction — advisory lock keyed by
 *       sop_id (serialized per SOP, PRN-006), next version = max+1, immutable
 *       snapshot insert, {@code sop_current} upsert, indicator cleared. The
 *       unique (sop_id, draft_revision) constraint turns a duplicate publish of
 *       the same revision into a 409. Any exception rolls all writes back.</li>
 * </ol>
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
  private final PublishService self;

  public PublishService(DraftRepository drafts,
                        PublicationRepository publications,
                        SopCurrentRepository currents,
                        ValidationService validation,
                        JdbcTemplate jdbc,
                        ObjectMapper mapper,
                        @Lazy PublishService self) {
    this.drafts = drafts;
    this.publications = publications;
    this.currents = currents;
    this.validation = validation;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.self = self;
  }

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
    Outcome outcome = validation.validate(draft.source());
    if (!outcome.valid()) {
      // FR-045: the indicator must survive the 422, so it commits in its own
      // transaction before the error is returned.
      self.recordPublishFailure(sopId);
      throw new ApiException(ErrorCode.VALIDATION_FAILED,
          "saved draft fails validation; current publication is unchanged",
          outcome.issues());
    }

    // The envelope sop_id must equal the draft path sop_id (FR-021 / IR-001).
    if (!sopId.equals(outcome.content().sopId())) {
      self.recordPublishFailure(sopId);
      throw new ApiException(ErrorCode.VALIDATION_FAILED,
          "content.sop_id does not match the draft sop_id",
          List.of(Issue.semantic(Contract.C_SOP_ID_MISMATCH,
              "content.sop_id must equal the draft sop_id", "content.sop_id")));
    }

    return self.commitPublish(sopId, requestedRevision, outcome.content());
  }

  /**
   * Persists the FR-045 failure indicator for the draft revision in its own
   * transaction so it survives the 422 response the caller then generates.
   */
  @Transactional
  public void recordPublishFailure(String sopId) {
    drafts.findById(sopId).ifPresent(d -> d.setPublishFailedAt(Instant.now()));
  }

  /**
   * The atomic publication (DES-008b / FR-042). All-or-nothing: any exception
   * (including the duplicate-revision constraint violation, which the handler
   * maps to 409 PUBLICATION_CONFLICT) rolls back every write.
   */
  @Transactional
  public Envelope commitPublish(String sopId, Long requestedRevision, Content content) {
    Draft draft = drafts.findById(sopId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
            "no saved draft exists for sop_id `" + sopId + "`"));
    if (draft.revision() != requestedRevision) {
      throw new ApiException(ErrorCode.STALE_REVISION,
          "draft revision " + requestedRevision
              + " is stale (the saved revision is " + draft.revision() + ")");
    }

    // Concurrency: serialize publishers of the same sop_id (DES-002, PRN-006).
    // Different sop_ids never block each other.
    acquireAdvisoryLock(sopId);

    int nextVersion = publications.nextVersionFor(sopId);
    Instant publishedAt = Instant.now();
    Envelope envelope = new Envelope(sopId, nextVersion, publishedAt.toString(), content);

    Publication publication = new Publication();
    publication.setSopId(sopId);
    publication.setVersion(nextVersion);
    publication.setDraftRevision(draft.revision());
    publication.setSource(draft.source());
    publication.setContentJson(toJson(content));
    publication.setEnvelopeJson(toJson(envelope));
    publication.setPublishedAt(publishedAt);
    publications.save(publication);

    SopCurrent current = currents.findById(sopId).orElseGet(SopCurrent::new);
    current.setSopId(sopId);
    current.setVersion(nextVersion);
    current.setPublishedAt(publishedAt);
    currents.save(current);

    // Successful publish clears the failure indicator (FR-045).
    draft.setPublishFailedAt(null);

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

  private void acquireAdvisoryLock(String sopId) {
    // Acquire a transaction-scoped advisory lock keyed by the sop_id hash
    // (DES-002, PRN-006 — "ordinary database coordination, not distributed
    // locking"). Postgres auto-widens the int4 returned by hashtext to the
    // bigint that pg_advisory_xact_lock expects. Released at transaction end.
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtext(?))",
        new Object[]{sopId},
        Object.class);
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
