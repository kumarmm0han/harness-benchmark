package com.sopdemo.publish;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopdemo.api.SopException;

import org.springframework.http.HttpStatus;

/**
 * Atomic publication write phase (ARC-006, DR-001): one transaction that
 * serializes concurrent publishes on the draft row
 * ({@code SELECT … FOR UPDATE}), re-checks the revision under the lock,
 * inserts the immutable snapshot, moves the current pointer, and clears the
 * failure indicator. The database constraints {@code (sop_id, version)} and
 * {@code (sop_id, source_revision)} are the final guard against concurrent
 * publication of the same saved revision (FR-042) — violations map to 409.
 */
@Component
public class PublicationTx {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PublicationTx(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public int publishLocked(String sopId, long requestedRevision, String source, Map<String, Object> content) {
        Long currentRevision =
                jdbc.queryForObject("SELECT revision FROM drafts WHERE sop_id = ? FOR UPDATE", Long.class, sopId);
        if (currentRevision == null) {
            throw new SopException(HttpStatus.NOT_FOUND, "draft-not-found", "No saved draft exists for '" + sopId + "'.");
        }
        if (currentRevision != requestedRevision) {
            throw stale(currentRevision, requestedRevision);
        }
        Integer nextVersion =
                jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) + 1 FROM sop_versions WHERE sop_id = ?",
                        Integer.class, sopId);
        String canonicalJson;
        try {
            canonicalJson = json.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("canonical content must serialize", e);
        }
        try {
            jdbc.update(
                    "INSERT INTO sop_versions (sop_id, version, source, canonical, source_revision, published_at) "
                            + "VALUES (?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)",
                    sopId, nextVersion, source, canonicalJson, requestedRevision);
            jdbc.update(
                    "INSERT INTO sops (sop_id, current_version) VALUES (?, ?) "
                            + "ON CONFLICT (sop_id) DO UPDATE SET current_version = EXCLUDED.current_version",
                    sopId, nextVersion);
            jdbc.update("UPDATE drafts SET publish_failed = false WHERE sop_id = ?", sopId);
        } catch (DataAccessException ex) {
            if (isUniqueViolation(ex)) {
                throw new SopException(HttpStatus.CONFLICT, "duplicate-publication",
                        "This saved draft revision has already been published; it cannot be published again.",
                        List.of());
            }
            throw ex;
        }
        return nextVersion;
    }

    private static SopException stale(long current, long requested) {
        return new SopException(HttpStatus.CONFLICT, "stale-revision",
                "The saved draft is at revision " + current + "; revision " + requested
                        + " cannot be published anymore.", List.of());
    }

    private static boolean isUniqueViolation(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
