package com.sopdemo.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sopdemo.AbstractPostgresSpringTest;
import com.sopdemo.content.Fixtures;

/**
 * TASK-008 verification: draft save/retrieve (FR-010), revision increments,
 * save clears the publication-failure flag (FR-045), identity (FR-001),
 * 413/400/404 envelopes (IR-001).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DraftApiTest extends AbstractPostgresSpringTest {

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

    @BeforeAll
    void cleanSop() {
        // Deterministic state even when the shared Testcontainers DB persists across classes.
        jdbc.update("DELETE FROM drafts WHERE sop_id IN ('BILL-001', 'SUPPORT-9')");
    }

    private ResponseEntity<Map> put(String sopId, Object body, HttpHeaders headers) {
        return rest.exchange("/api/v1/drafts/" + sopId, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    @Order(1)
    @DisplayName("first save -> 200 with revision 1 and {sop_id, revision, source}")
    void firstSaveReturnsRevision1() {
        ResponseEntity<Map> res = put("BILL-001", Map.of("source", Fixtures.validRefundDoc()), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("sop_id", "BILL-001");
        assertThat(res.getBody()).containsEntry("revision", 1);
        assertThat(res.getBody().get("source")).isEqualTo(Fixtures.validRefundDoc());
    }

    @Test
    @Order(2)
    @DisplayName("second save increments to revision 2 (FR-010)")
    void secondSaveIncrementsRevision() {
        ResponseEntity<Map> res = put("BILL-001", Map.of("source", Fixtures.validAnswerDoc()), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("revision", 2);
    }

    @Test
    @Order(3)
    @DisplayName("saved source is preserved verbatim and retrievable")
    void savedSourceIsPreserved() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/drafts/BILL-001", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("sop_id", "BILL-001");
        assertThat(res.getBody()).containsEntry("revision", 2);
        assertThat(res.getBody()).containsEntry("publish_failed", false);
        assertThat(res.getBody().get("source")).isEqualTo(Fixtures.validAnswerDoc());
    }

    @Test
    @Order(4)
    @DisplayName("author draft list contains the saved draft with revision and flag (FR-050)")
    void draftListShowsSavedDrafts() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/drafts", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) res.getBody().get("drafts");
        assertThat(list).isNotEmpty();
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertThat(first).extracting("sop_id", "revision", "publish_failed")
                .contains("BILL-001", 2, false);
    }

    @Test
    @Order(5)
    @DisplayName("saving after a failed publish clears the failure indicator (FR-045)")
    void saveClearsPublishFailedFlag() {
        jdbc.update("UPDATE drafts SET publish_failed = true WHERE sop_id = ?", "BILL-001");
        ResponseEntity<Map> flagged = rest.exchange("/api/v1/drafts/BILL-001", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(flagged.getBody()).containsEntry("publish_failed", true);

        ResponseEntity<Map> saved = put("BILL-001", Map.of("source", Fixtures.validRefundDoc()), AUTHOR);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("revision", 3);

        ResponseEntity<Map> after = rest.exchange("/api/v1/drafts/BILL-001", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(after.getBody()).containsEntry("publish_failed", false);
    }

    @Test
    @Order(6)
    @DisplayName("saving is allowed even when content is invalid (IR-001)")
    void invalidContentCanBeSaved() {
        ResponseEntity<Map> res = put("SUPPORT-9", Map.of("source", "not a valid sop at all"), AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("sop_id", "SUPPORT-9");
    }

    @Test
    @Order(10)
    @DisplayName("unknown draft -> 404 draft-not-found")
    void missingDraftIs404() {        ResponseEntity<Map> res = rest.exchange("/api/v1/drafts/NOPE-1", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).containsEntry("code", "draft-not-found");
    }

    @Test
    @Order(11)
    @DisplayName("consumer gets 403 on draft endpoints (author-only)")
    void consumerIsForbiddenOnDrafts() {
        ResponseEntity<Map> putRes = put("CON-1", Map.of("source", "x"), CONSUMER);
        assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> listRes = rest.exchange("/api/v1/drafts", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> getRes = rest.exchange("/api/v1/drafts/BILL-001", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(12)
    @DisplayName("missing identity -> 401")
    void missingIdentityIs401() {
        HttpHeaders none = new HttpHeaders();
        ResponseEntity<Map> res = rest.exchange("/api/v1/drafts", HttpMethod.GET,
                new HttpEntity<>(none), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("code", "unauthorized");
    }

    @Test
    @Order(20)
    @DisplayName("oversized source -> 413, invalid sop_id -> 400, missing field -> 400")
    void envelopeErrors() {
        ResponseEntity<Map> big = put("BILL-001", Map.of("source", "b".repeat(65_537)), AUTHOR);
        assertThat(big.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(big.getBody()).containsEntry("code", "source-too-large");

        ResponseEntity<Map> badId = put("lowercase", Map.of("source", "x"), AUTHOR);
        assertThat(badId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badId.getBody()).containsEntry("code", "invalid-request");

        ResponseEntity<Map> noSource = put("BILL-001", Map.of("nope", 1), AUTHOR);
        assertThat(noSource.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noSource.getBody()).containsEntry("code", "invalid-request");

        // Failed saves must not have changed the draft.
        ResponseEntity<Map> after = rest.exchange("/api/v1/drafts/BILL-001", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(after.getBody()).containsEntry("revision", 3);
    }
}
