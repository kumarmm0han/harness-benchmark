package com.sopdemo.sop;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopdemo.api.InvalidRequestException;
import com.sopdemo.api.SopException;
import com.sopdemo.identity.Authorize;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Published-SOP read model (IR-001):
 * <ul>
 *   <li>{@code GET /api/v1/sops?domain=&risk=} — current published summaries (both roles, FR-050);
 *       invalid filter values are 400.</li>
 *   <li>{@code GET /api/v1/sops/{sop_id}} — current canonical snapshot (both roles, FR-052/053);
 *       404 when nothing has been published (FR-045) — never draft data.</li>
 *   <li>{@code GET /api/v1/sops/{sop_id}/versions/{version}} — immutable historical snapshot
 *       (author only, FR-043).</li>
 * </ul>
 * Read queries are plain SELECTs on the immutable version rows; a reader either
 * sees the previous current version or the next one, never a mix (ARC-005).
 */
@RestController
public class SopController {

    private static final List<String> DOMAINS = List.of("Billing", "Support");
    private static final List<String> RISKS = List.of("low", "medium");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {

    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SopController(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/api/v1/sops")
    public Map<String, Object> list(@RequestParam(required = false) String domain,
                                    @RequestParam(required = false) String risk,
                                    HttpServletRequest request) {
        Authorize.current(request); // any demo identity (author or consumer); 401 otherwise
        if (domain != null && !DOMAINS.contains(domain)) {
            throw new InvalidRequestException("domain must be one of: Billing, Support.");
        }
        if (risk != null && !RISKS.contains(risk)) {
            throw new InvalidRequestException("risk must be one of: low, medium.");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT v.sop_id, v.canonical ->> 'title' AS title, s.current_version AS version, "
                        + "v.canonical ->> 'domain' AS domain, v.canonical ->> 'risk_level' AS risk_level "
                        + "FROM sops s JOIN sop_versions v ON (v.sop_id = s.sop_id AND v.version = s.current_version) ");
        List<Object> args = new ArrayList<>();
        if (domain != null) {
            sql.append("WHERE v.canonical ->> 'domain' = ? ");
            args.add(domain);
        }
        if (risk != null) {
            sql.append(domain != null ? "AND " : "WHERE ").append("v.canonical ->> 'risk_level' = ? ");
            args.add(risk);
        }
        sql.append("ORDER BY v.sop_id");
        List<Map<String, Object>> sops = new ArrayList<>();
        jdbc.query(sql.toString(),
                new ArgumentPreparedStatementSetter(args.toArray()),
                rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sop_id", rs.getString("sop_id"));
                    row.put("title", rs.getString("title"));
                    row.put("version", rs.getInt("version"));
                    row.put("domain", rs.getString("domain"));
                    row.put("risk_level", rs.getString("risk_level"));
                    sops.add(row);
                });
        return Map.of("sops", sops);
    }

    @GetMapping("/api/v1/sops/{sopId}")
    public Map<String, Object> current(@PathVariable String sopId, HttpServletRequest request) {
        Authorize.current(request);
        Map<String, Object> row = selectCurrent(sopId);
        if (row == null) {
            throw new SopException(HttpStatus.NOT_FOUND, "sop-not-found",
                    "No published version exists for '" + sopId + "' yet.", List.of());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sop_id", sopId);
        out.put("version", row.get("version"));
        out.put("content", row.get("content"));
        return out;
    }

    @GetMapping("/api/v1/sops/{sopId}/versions/{version}")
    public Map<String, Object> version(@PathVariable String sopId, @PathVariable int version,
                                       HttpServletRequest request) {
        Authorize.requireAuthor(request);
        final String[] canonical = new String[1];
        Object[] meta = new Object[2];
        try {
            jdbc.query(
                    "SELECT canonical, source_revision, published_at FROM sop_versions "
                            + "WHERE sop_id = ? AND version = ?",
                    new ArgumentPreparedStatementSetter(new Object[] { sopId, version }),
                    rs -> {
                        canonical[0] = rs.getString("canonical");
                        meta[0] = rs.getLong("source_revision");
                        meta[1] = rs.getTimestamp("published_at").toInstant();
                    });
        } catch (EmptyResultDataAccessException e) {
            throw new SopException(HttpStatus.NOT_FOUND, "version-not-found",
                    "Version " + version + " of '" + sopId + "' does not exist.", List.of());
        }
        if (canonical[0] == null) {
            throw new SopException(HttpStatus.NOT_FOUND, "version-not-found",
                    "Version " + version + " of '" + sopId + "' does not exist.", List.of());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sop_id", sopId);
        out.put("version", version);
        out.put("published_revision", meta[0]);
        out.put("published_at", OffsetDateTime.ofInstant((java.time.Instant) meta[1], java.time.ZoneOffset.UTC));
        out.put("content", parseJson(canonical[0]));
        return out;
    }

    private Map<String, Object> selectCurrent(String sopId) {
        String sql = "SELECT s.current_version AS version, v.canonical AS canonical "
                + "FROM sops s JOIN sop_versions v ON (v.sop_id = s.sop_id AND v.version = s.current_version) "
                + "WHERE s.sop_id = ?";
        final String[] canonical = new String[1];
        final int[] version = new int[1];
        try {
            jdbc.query(sql, new ArgumentPreparedStatementSetter(new Object[] { sopId }), rs -> {
                canonical[0] = rs.getString("canonical");
                version[0] = rs.getInt("version");
            });
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        if (canonical[0] == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", version[0]);
        row.put("content", parseJson(canonical[0]));
        return row;
    }

    private Map<String, Object> parseJson(String jsonText) {
        try {
            return json.readValue(jsonText, MAP);
        } catch (Exception e) {
            throw new IllegalStateException("stored canonical snapshot must be valid JSON", e);
        }
    }
}
