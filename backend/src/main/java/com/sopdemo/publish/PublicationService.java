package com.sopdemo.publish;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sopdemo.api.SopException;
import com.sopdemo.content.ContentEngine.ContentAnalysis;
import com.sopdemo.content.ContentEngine;
import com.sopdemo.content.Codes;
import com.sopdemo.drafts.DraftStore;
import com.sopdemo.drafts.DraftStore.Draft;

/**
 * Publication pipeline (FR-042, FR-043, FR-045):
 * <ol>
 *   <li>fast checks outside any lock — draft exists (404), revision is current (409);</li>
 *   <li>server-side revalidation of the saved source (FR-042); rejected content is 422
 *       with issues and leaves the previous current version untouched while the
 *       publication-failure indicator is set for the draft (FR-045);</li>
 *   <li>sop_id of the source must match the draft path (IR-001) else 422;</li>
 *   <li>atomic write phase ({@link PublicationTx}) returning the stored version.</li>
 * </ol>
 * A successful publish returns the stored canonical snapshot (FR-042).
 */
@Service
public class PublicationService {

    public record Publication(String sopId, int version, Instant publishedAt, Map<String, Object> content) {
    }

    private final DraftStore drafts;
    private final ContentEngine engine;
    private final PublicationTx tx;

    public PublicationService(DraftStore drafts, ContentEngine engine, PublicationTx tx) {
        this.drafts = drafts;
        this.engine = engine;
        this.tx = tx;
    }

    public Publication publish(String sopId, long requestedRevision) {
        Draft draft = drafts.find(sopId)
                .orElseThrow(() -> new SopException(HttpStatus.NOT_FOUND, "draft-not-found",
                        "No saved draft exists for '" + sopId + "'; save the draft before publishing.", List.of()));
        if (draft.revision() != requestedRevision) {
            throw new SopException(HttpStatus.CONFLICT, "stale-revision",
                    "The saved draft is at revision " + draft.revision() + "; revision " + requestedRevision
                            + " cannot be published anymore.", List.of());
        }
        ContentAnalysis analysis = engine.analyze(draft.source());
        if (!analysis.valid()) {
            drafts.markPublishFailed(sopId); // FR-045: keep previous version, flag this draft
            throw new SopException(HttpStatus.UNPROCESSABLE_ENTITY, "publication-rejected",
                    "The draft is not valid; the previously published version remains current.",
                    issueMaps(analysis));
        }
        Object declared = analysis.content().get("sop_id");
        if (!sopId.equals(declared)) {
            drafts.markPublishFailed(sopId);
            throw new SopException(HttpStatus.UNPROCESSABLE_ENTITY, Codes.SOP_ID_MISMATCH,
                    "The source declares sop_id '" + declared + "' but the draft is saved under '" + sopId + "'.",
                    List.of());
        }
        PublicationTx.WriteResult result =
                tx.publishLocked(sopId, requestedRevision, draft.source(), analysis.content());
        return new Publication(sopId, result.version(), result.publishedAt(), analysis.content());
    }

    private static List<Map<String, Object>> issueMaps(ContentAnalysis analysis) {
        return analysis.issues().stream()
                .map(i -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("code", i.code());
                    m.put("stage", i.stage());
                    m.put("message", i.message());
                    m.put("path", i.path());
                    return m;
                })
                .toList();
    }
}
