package com.sopdemo.drafts;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Draft storage (DR-001, FR-010): one editable draft per {@code sop_id} with a
 * server-owned revision that increments on every save, plus the
 * publication-failure indicator (FR-045). Saves never touch published rows.
 */
@Repository
public class DraftStore {

    public record Draft(String sopId, String source, long revision, boolean publishFailed, OffsetDateTime savedAt) {

        public record Summary(String sopId, long revision, boolean publishFailed, OffsetDateTime savedAt) {

            public Summary(Draft draft) {
                this(draft.sopId(), draft.revision(), draft.publishFailed(), draft.savedAt());
            }
        }
    }

    private static final RowMapper<Draft> MAPPER = (rs, n) -> new Draft(
            rs.getString("sop_id"),
            rs.getString("source"),
            rs.getLong("revision"),
            rs.getBoolean("publish_failed"),
            rs.getObject("saved_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public DraftStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Draft> find(String sopId) {
        return jdbc.query("SELECT sop_id, source, revision, publish_failed, saved_at FROM drafts WHERE sop_id = ?",
                MAPPER, sopId).stream().findFirst();
    }

    /** All drafts ordered by {@code sop_id} (FR-050 author draft list). */
    public java.util.List<Draft> list() {
        return jdbc.query("SELECT sop_id, source, revision, publish_failed, saved_at FROM drafts ORDER BY sop_id",
                MAPPER);
    }

    /**
     * Insert or update the draft and return the new revision (FR-010).
     * A save always clears the publication-failure indicator (FR-045).
     */
    public long save(String sopId, String source) {
        Long revision = jdbc.queryForObject(
                """
                INSERT INTO drafts (sop_id, source, revision)
                VALUES (?, ?, 1)
                ON CONFLICT (sop_id) DO UPDATE
                    SET source = EXCLUDED.source,
                        revision = drafts.revision + 1,
                        publish_failed = false,
                        saved_at = CURRENT_TIMESTAMP
                RETURNING revision
                """, Long.class, sopId, source);
        return revision;
    }

    /** Set the failure indicator for a failed publish of this draft (FR-045). */
    public void markPublishFailed(String sopId) {
        jdbc.update("UPDATE drafts SET publish_failed = true WHERE sop_id = ?", sopId);
    }

    public boolean exists(String sopId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM drafts WHERE sop_id = ?", Integer.class, sopId);
        return n != null && n > 0;
    }
}
