package com.sopdemo.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sopdemo.AbstractPostgresSpringTest;
import com.sopdemo.content.Fixtures;

/**
 * TASK-007 verification: {@code POST /api/v1/validate} contract (IR-001, FR-034, FR-020).
 * 200 for both valid and invalid content (content null when invalid); 400 for
 * malformed envelopes; 401/403 for identity; 413 for oversized sources; read-only.
 */
class ValidateApiTest extends AbstractPostgresSpringTest {

    /** spec §3 mutation: drop the refund limit AND the escalation boundary. */
    private static String docMissingLimitAndEscalation() {
        return Fixtures.validRefundDoc()
                .replace("  max_amount: 150", "")
                .replace("""
                        escalation:
                          - action_id: a_refund
                            input: refund_amount
                            op: gt
                            amount: 150
                            target_action_id: a_escalate
                        """,
                        "escalation: []\n");
    }

    private static final HttpHeaders AUTHOR = new HttpHeaders();
    private static final HttpHeaders CONSUMER = new HttpHeaders();

    static {
        AUTHOR.set("X-Demo-User", "demo-author");
        CONSUMER.set("X-Demo-User", "demo-consumer");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private ResponseEntity<Map> post(Object body, HttpHeaders headers) {
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return rest.exchange("/api/v1/validate", HttpMethod.POST, entity, Map.class);
    }

    @Test
    @DisplayName("valid template -> 200 valid:true with canonical content")
    void validDocumentReturnsCanonicalContent() {
        ResponseEntity<Map> res = post(Map.of("source", Fixtures.validRefundDoc()), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("valid", true);
        assertThat(res.getBody()).containsEntry("issues", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) res.getBody().get("content");
        assertThat(content).isNotNull();
        assertThat(content).containsKeys("sop_id", "title", "inputs", "rules", "actions", "boundaries");
        assertThat(content.get("sop_id")).isEqualTo("BILL-001");
    }

    @Test
    @DisplayName("refunds missing limit and escalation -> 200 valid:false, content null, readable issues")
    void invalidDocumentReturnsIssuesAndNullContent() {
        ResponseEntity<Map> res = post(Map.of("source", docMissingLimitAndEscalation()), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("valid", false);
        assertThat(res.getBody()).containsEntry("content", null);
        List<?> issues = (List<?>) res.getBody().get("issues");
        assertThat(issues).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) issues.get(0);
        assertThat(first).containsKeys("code", "stage", "message", "path");
        String codes = issues.stream()
                .map(i -> ((Map<?, ?>) i).get("code").toString())
                .reduce("", (a, b) -> a + "," + b);
        assertThat(codes).contains("refund-limit-missing").contains("refund-escalation-missing");
        // Human-readable, no parser internals (NFR-020).
        for (Object o : issues) {
            assertThat(((Map<?, ?>) o).get("message").toString()).doesNotContain("snakeyaml", "SnakeYAML", "at com.sopdemo");
        }
    }

    @Test
    @DisplayName("issues come back sorted deterministically by (path, code)")
    void issuesAreSortedDeterministically() {
        String doc = Fixtures.validRefundDoc().replace("sop_id: BILL-001", "sop_id: BILL_X"); // invalid charset
        ResponseEntity<Map> res = post(Map.of("source", doc), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> issues = (List<Map<?, ?>>) res.getBody().get("issues");
        assertThat(issues).isNotEmpty();
        for (int i = 0; i + 1 < issues.size(); i++) {
            String a = issues.get(i).get("path") + "|" + issues.get(i).get("code");
            String b = issues.get(i + 1).get("path") + "|" + issues.get(i + 1).get("code");
            assertThat(a).as("issues must be sorted by (path, code)").isLessThanOrEqualTo(b);
        }
    }

    @Test
    @DisplayName("missing identity -> 401; consumer -> 403 (author-only op)")
    void identityAndRoleEnforced() {
        ResponseEntity<Map> noId = post(Map.of("source", Fixtures.validRefundDoc()), new HttpHeaders());
        assertThat(noId.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noId.getBody()).containsEntry("code", "unauthorized");

        ResponseEntity<Map> consumer = post(Map.of("source", Fixtures.validRefundDoc()), CONSUMER);
        assertThat(consumer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(consumer.getBody()).containsEntry("code", "forbidden");
    }

    @Test
    @DisplayName("malformed envelopes -> 400 with normalized body (IR-001)")
    void malformedEnvelopesReturn400() {
        ResponseEntity<Map> noBody = post(null, AUTHOR);
        assertThat(noBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noBody.getBody()).containsEntry("code", "invalid-request");

        ResponseEntity<Map> missingField = post(Map.of("s", "x"), AUTHOR);
        assertThat(missingField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> wrongType = post(Map.of("source", 42), AUTHOR);
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        json.set("X-Demo-User", "demo-author");
        ResponseEntity<Map> notAnObject =
                rest.exchange("/api/v1/validate", HttpMethod.POST, new HttpEntity<>("[1,2]", json), Map.class);
        assertThat(notAnObject.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notAnObject.getBody()).containsEntry("code", "invalid-request");
    }

    @Test
    @DisplayName("oversized source -> 413 source-too-large (FR-020, IR-001)")
    void oversizedSourceReturns413() {
        String oversized = "a".repeat(65_537);
        ResponseEntity<Map> res = post(Map.of("source", oversized), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).containsEntry("code", "source-too-large");
        assertThat(res.getBody().get("issues")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("validation is read-only: it creates no draft rows (FR-034)")
    void validateDoesNotPersistState() {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM drafts", Integer.class);
        post(Map.of("source", Fixtures.validRefundDoc()), AUTHOR);
        post(Map.of("source", docMissingLimitAndEscalation()), AUTHOR);
        Integer after = jdbc.queryForObject("SELECT count(*) FROM drafts", Integer.class);
        assertThat(after).isEqualTo(before);
    }
}
