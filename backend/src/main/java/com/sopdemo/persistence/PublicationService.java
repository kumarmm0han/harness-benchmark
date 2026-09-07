package com.sopdemo.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopdemo.domain.PolicyResult;
import com.sopdemo.domain.issue.IssueCode;
import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Published;
import com.sopdemo.domain.validate.ValidationService;
import com.sopdemo.web.ApiException;
import com.sopdemo.web.dto.CatalogDtos.SopSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional publication (DES-008, ARC-004/005, FR-042/043/045, PRN-006).
 *
 * <p>Publish is one transaction serialised per {@code sop_id} by a draft-row lock
 * ({@code SELECT ... FOR UPDATE}):
 * <ol>
 *   <li>lock + read the saved draft, else 404;</li>
 *   <li>revision must match the saved draft, else 409 (stale);</li>
 *   <li>re-validate the saved source; invalid → persist the failure indicator + 422,
 *       leaving the previous current version untouched (FR-045);</li>
 *   <li>a valid publish inserts the immutable snapshot at {@code MAX(version)+1},
 *       upserts the current pointer, and clears the failure indicator.</li>
 * </ol>
 * The {@code UNIQUE(sop_id,version)} / {@code UNIQUE(sop_id,draft_revision)} constraints are the
 * final backstop — a duplicate revision or colliding version rolls back and maps to 409. Reading
 * the current pointer or a specific version returns a stable snapshot; absent → 404 (FR-043/045).
 */
@Service
public class PublicationService {

    private final DraftRepository drafts;
    private final PublicationRepository publications;
    private final ValidationService validation;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public PublicationService(
            DraftRepository drafts,
            PublicationRepository publications,
            ValidationService validation,
            ObjectMapper mapper,
            PlatformTransactionManager txManager) {
        this.drafts = drafts;
        this.publications = publications;
        this.validation = validation;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    public Published publish(String sopId, long requestedRevision) {
        Outcome outcome;
        try {
            outcome = tx.execute(status -> {
                Draft draft = drafts.findForUpdate(sopId);
                if (draft == null) {
                    return new NotFound();
                }
                if (draft.revision() != requestedRevision) {
                    return new Conflict(
                            "requested revision "
                                    + requestedRevision
                                    + " does not match saved revision "
                                    + draft.revision()
                                    + " (stale)");
                }
                PolicyResult result = validation.validate(draft.source());
                if (!result.valid()) {
                    drafts.setPublicationFailed(sopId, true);
                    return new Invalid(result.issues());
                }
                Content content = result.content();
                if (!content.sopId().equals(sopId)) {
                    ValidationIssue issue = ValidationIssue.of(
                            IssueCode.SOP_ID_MISMATCH,
                            "documented sop_id " + content.sopId() + " does not match the requested path " + sopId,
                            "frontmatter.sop_id");
                    drafts.setPublicationFailed(sopId, true);
                    return new Invalid(List.of(issue));
                }
                if (publications.existsForRevision(sopId, draft.revision())) {
                    return new Conflict("revision " + draft.revision() + " is already published (duplicate)");
                }
                int version = publications.nextVersion(sopId);
                Instant at = Instant.now();
                publications.insert(sopId, version, draft.revision(), draft.source(), toJson(content), at);
                publications.upsertCurrent(sopId, version);
                drafts.setPublicationFailed(sopId, false);
                return new Ok(new Published(sopId, version, at, content));
            });
        } catch (DuplicateKeyException duplicate) {
            throw ApiException.conflict(
                    "conflicting publish: this revision/version is already published for " + sopId);
        }
        if (outcome instanceof Ok ok) {
            return ok.published;
        }
        if (outcome instanceof Invalid invalid) {
            throw ApiException.unprocessable(
                    "publication content was rejected (previous current version retained)", invalid.issues);
        }
        if (outcome instanceof Conflict conflict) {
            throw ApiException.conflict(conflict.message);
        }
        throw ApiException.notFound("no saved draft for sop_id " + sopId);
    }

    /** Current canonical snapshot for consumers and authors (FR-043/050/053), or null. */
    public Published currentPublished(String sopId) {
        Publication p = publications.findCurrent(sopId);
        return p == null ? null : toPublished(p);
    }

    /** Immutable historical snapshot for the author (FR-043), or null. */
    public Published publishedVersion(String sopId, int version) {
        Publication p = publications.findVersion(sopId, version);
        return p == null ? null : toPublished(p);
    }

    /** Current published summaries, filtered (AND) and sorted by sop_id (FR-050). */
    public List<SopSummary> list(String domain, String risk) {
        return publications.listCurrent().stream()
                .map(row -> new Entry(row, fromJson(row.snapshot())))
                .filter(e -> domain == null || e.content().domain().equals(domain))
                .filter(e -> risk == null || e.content().riskLevel().equals(risk))
                .sorted(Comparator.comparing(e -> e.row().sopId()))
                .map(e -> new SopSummary(
                        e.row().sopId(),
                        e.content().title(),
                        e.row().version(),
                        e.content().domain(),
                        e.content().riskLevel()))
                .toList();
    }

    private Published toPublished(Publication p) {
        return new Published(p.sopId(), p.version(), p.publishedAt(), fromJson(p.snapshot()));
    }

    private String toJson(Content content) {
        try {
            return mapper.writeValueAsString(content);
        } catch (IOException e) {
            throw new UncheckedIOException("serialise canonical snapshot", e);
        }
    }

    private Content fromJson(String json) {
        try {
            return mapper.readValue(json, Content.class);
        } catch (IOException e) {
            throw new UncheckedIOException("deserialise canonical snapshot", e);
        }
    }

    private record Entry(Publication row, Content content) {}

    interface Outcome {}

    static final class Ok implements PublicationService.Outcome {
        final Published published;

        Ok(Published p) {
            this.published = p;
        }
    }

    static final class Invalid implements PublicationService.Outcome {
        final List<ValidationIssue> issues;

        Invalid(List<ValidationIssue> issues) {
            this.issues = issues;
        }
    }

    static final class Conflict implements PublicationService.Outcome {
        final String message;

        Conflict(String message) {
            this.message = message;
        }
    }

    static final class NotFound implements PublicationService.Outcome {}
}
