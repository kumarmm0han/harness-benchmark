package com.sopdemo.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Thin JDBC over {@code sop_publication} / {@code sop_current} (DES-007/008). No business
 * rules here — the transactional publish and the reads are coordinated by
 * {@link PublicationService} (PRN-003, PRN-004). The immutable snapshot is stored as its
 * JSON string and handed back opaque; callers deserialize it with the shared {@code ObjectMapper}.
 */
@Repository
public class PublicationRepository {

    private static final String COLS = "sop_id, version, draft_revision, source, snapshot, published_at";
    private static final String COLS_P = "p.sop_id, p.version, p.draft_revision, p.source, p.snapshot, p.published_at";

    private static final RowMapper<Publication> MAPPER =
            (rs, rowNum) ->
                    new Publication(
                            rs.getString("sop_id"),
                            rs.getInt("version"),
                            rs.getLong("draft_revision"),
                            rs.getString("source"),
                            rs.getString("snapshot"),
                            rs.getTimestamp("published_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public PublicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Publication insert(
            String sopId, int version, long draftRevision, String source, String snapshot, Instant publishedAt) {
        String sql =
                "INSERT INTO sop_publication (sop_id, version, draft_revision, source, snapshot, published_at) "
                        + "VALUES (:sopId, :version, :draftRevision, :source, CAST(:snapshot AS jsonb), :publishedAt) "
                        + "RETURNING "
                        + COLS;
        MapSqlParameterSource p = new MapSqlParameterSource("sopId", sopId);
        p.addValue("version", version);
        p.addValue("draftRevision", draftRevision);
        p.addValue("source", source);
        p.addValue("snapshot", snapshot);
        p.addValue("publishedAt", Timestamp.from(publishedAt));
        return jdbc.queryForObject(sql, p, MAPPER);
    }

    public boolean existsForRevision(String sopId, long draftRevision) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM sop_publication WHERE sop_id = :sopId AND draft_revision = :rev",
                new MapSqlParameterSource("sopId", sopId).addValue("rev", draftRevision),
                Integer.class);
        return n != null && n > 0;
    }

    public Publication findCurrent(String sopId) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + COLS_P + " FROM sop_publication p "
                            + "JOIN sop_current c ON c.sop_id = p.sop_id AND c.version = p.version "
                            + "WHERE p.sop_id = :sopId",
                    new MapSqlParameterSource("sopId", sopId),
                    MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Publication findVersion(String sopId, int version) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + COLS + " FROM sop_publication WHERE sop_id = :sopId AND version = :version",
                    new MapSqlParameterSource("sopId", sopId).addValue("version", version),
                    MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** Next consecutive version for {@code sop_id} (DES-008): {@code MAX(version)+1}, or 1. */
    public int nextVersion(String sopId) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM sop_publication WHERE sop_id = :sopId",
                new MapSqlParameterSource("sopId", sopId),
                Integer.class);
        return next == null ? 1 : next;
    }

    /** Point the single current-version pointer at a new version (DES-008, FR-042). */
    public void upsertCurrent(String sopId, int version) {
        jdbc.update(
                "INSERT INTO sop_current (sop_id, version) VALUES (:sopId, :version) "
                        + "ON CONFLICT (sop_id) DO UPDATE SET version = :version",
                new MapSqlParameterSource("sopId", sopId).addValue("version", version));
    }

    /** All currently-current rows (the {@code sop_current} pointer set), sorted by sop_id. */
    public List<Publication> listCurrent() {
        return jdbc.query(
                "SELECT " + COLS_P + " FROM sop_publication p "
                        + "JOIN sop_current c ON c.sop_id = p.sop_id AND c.version = p.version "
                        + "ORDER BY p.sop_id",
                new MapSqlParameterSource(),
                MAPPER);
    }
}
