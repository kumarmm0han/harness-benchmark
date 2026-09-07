package com.sopdemo.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Thin JDBC repository over {@code sop_draft} (DES-007). Atomic insert-or-update with an
 * incrementing server-assigned revision (FR-010). Saving clears the publication-failure
 * indicator (FR-045). No business rules here — only persistence (PRN-003, PRN-004).
 */
@Repository
public class DraftRepository {

    private static final String COLS = "sop_id, source, revision, publication_failed, updated_at";

    private static final RowMapper<Draft> MAPPER = (rs, rowNum) ->
            new Draft(
                    rs.getString("sop_id"),
                    rs.getString("source"),
                    rs.getLong("revision"),
                    rs.getBoolean("publication_failed"),
                    Instant.ofEpochMilli(rs.getTimestamp("updated_at").getTime()));

    private final NamedParameterJdbcTemplate jdbc;

    public DraftRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert-or-update: first save yields revision 1; each subsequent save increments it. */
    public Draft upsert(String sopId, String source) {
        String sql = """
                INSERT INTO sop_draft (sop_id, source, revision, publication_failed, updated_at)
                VALUES (:sopId, :source, 1, FALSE, now())
                ON CONFLICT (sop_id) DO UPDATE
                    SET source = :source,
                        revision = sop_draft.revision + 1,
                        publication_failed = FALSE,
                        updated_at = now()
                RETURNING
                """ + " " + COLS;
        MapSqlParameterSource p = new MapSqlParameterSource("sopId", sopId);
        p.addValue("source", source);
        return jdbc.queryForObject(sql, p, MAPPER);
    }

    public Optional<Draft> find(String sopId) {
        try {
            Draft d = jdbc.queryForObject(
                    "SELECT " + COLS + " FROM sop_draft WHERE sop_id = :sopId",
                    new MapSqlParameterSource("sopId", sopId), MAPPER);
            return Optional.ofNullable(d);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Draft> list() {
        return jdbc.query("SELECT " + COLS + " FROM sop_draft ORDER BY sop_id", new MapSqlParameterSource(), MAPPER);
    }

    /** Locking read (DES-008): serializes concurrent publishes of the same {@code sop_id}. */
    public Draft findForUpdate(String sopId) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + COLS + " FROM sop_draft WHERE sop_id = :sopId FOR UPDATE",
                    new MapSqlParameterSource("sopId", sopId), MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void setPublicationFailed(String sopId, boolean failed) {
        jdbc.update(
                "UPDATE sop_draft SET publication_failed = :f, updated_at = now() WHERE sop_id = :sopId",
                params().addValue("f", failed).addValue("sopId", sopId));
    }

    public boolean exists(String sopId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM sop_draft WHERE sop_id = :sopId",
                new MapSqlParameterSource("sopId", sopId), Integer.class);
        return n != null && n > 0;
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }
}
