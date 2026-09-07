package com.sopdemo.sop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * TASK-009 verification (read model): list + domain/risk filters with fixed
 * semantics (FR-050), current snapshot for both roles (FR-052/053),
 * 400 for invalid filters, 404 for unpublished SOPs (FR-045), consumers never
 * see drafts (FR-001).
 */
class ReadApiTest extends AbstractPostgresSpringTest {

    private static final String[] IDS = { "READ-1", "READ-2" };

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
    @SuppressWarnings("unchecked")
    void setup() {
        for (String id : IDS) {
            jdbc.update("DELETE FROM sop_versions WHERE sop_id = ?", id);
            jdbc.update("DELETE FROM sops WHERE sop_id = ?", id);
            jdbc.update("DELETE FROM drafts WHERE sop_id = ?", id);
        }
        // READ-1: Billing / medium ; READ-2: Support / low
        String d1 = Fixtures.validRefundDoc()
                .replace("sop_id: BILL-001", "sop_id: READ-1");
        String d2 = Fixtures.validAnswerDoc()
                .replace("sop_id: SUP-ANSWER", "sop_id: READ-2");
        long r1 = save("READ-1", d1);
        publish("READ-1", r1);
        long r2 = save("READ-2", d2);
        publish("READ-2", r2);

        // Author-only draft that must never leak into consumer views (FR-001).
        save("HIDDEN-DRAFT", Fixtures.validRefundDoc().replace("sop_id: BILL-001", "sop_id: HIDDEN-DRAFT"));
    }

    private long save(String sopId, String source) {
        ResponseEntity<Map> r = rest.exchange("/api/v1/drafts/" + sopId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("source", source), AUTHOR), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("revision")).longValue();
    }

    private void publish(String sopId, long revision) {
        ResponseEntity<Map> r = rest.exchange("/api/v1/sops/" + sopId + "/publish", HttpMethod.POST,
                new HttpEntity<>(Map.of("revision", revision), AUTHOR), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String query, HttpHeaders headers) {
        ResponseEntity<Map> res = rest.exchange("/api/v1/sops" + query, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) res.getBody().get("sops");
    }

    @Test
    @DisplayName("both roles see the same current published summaries (FR-050, FR-053)")
    void listVisibleToBothRoles() {
        List<Map<String, Object>> asAuthor = list("", AUTHOR);
        List<Map<String, Object>> asConsumer = list("", CONSUMER);
        assertThat(asAuthor).hasSizeGreaterThanOrEqualTo(2);
        assertThat(asConsumer).isEqualTo(asAuthor);
        Map<String, Object> one = asAuthor.stream().filter(m -> "READ-1".equals(m.get("sop_id"))).findFirst().orElseThrow();
        assertThat(one).containsEntry("title", "Duplicate Charge Refund");
        assertThat(one).containsEntry("domain", "Billing");
        assertThat(one).containsEntry("risk_level", "medium");
        assertThat(((Number) one.get("version")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("domain and risk filters have fixed semantics")
    void filtersWork() {
        List<Map<String, Object>> billing = list("?domain=Billing", CONSUMER);
        assertThat(billing).allSatisfy(m -> assertThat(m.get("domain")).isEqualTo("Billing"));
        assertThat(billing.stream().map(m -> m.get("sop_id")).toList()).contains("READ-1");

        List<Map<String, Object>> low = list("?risk=low", CONSUMER);
        assertThat(low).allSatisfy(m -> assertThat(m.get("risk_level")).isEqualTo("low"));
        assertThat(low.stream().map(m -> m.get("sop_id")).toList()).contains("READ-2");

        List<Map<String, Object>> combined = list("?domain=Billing&risk=low", CONSUMER);
        assertThat(combined).isEmpty();
        List<Map<String, Object>> both = list("?domain=Billing&risk=medium", CONSUMER);
        assertThat(both.stream().map(m -> m.get("sop_id")).toList()).contains("READ-1");
    }

    @Test
    @DisplayName("invalid filter values -> 400 (IR-001)")
    void invalidFiltersAre400() {
        for (String q : List.of("?domain=Nope", "?risk=high", "?domain=billing", "?risk=HIGH")) {
            ResponseEntity<Map> res = rest.exchange("/api/v1/sops" + q, HttpMethod.GET,
                    new HttpEntity<>(CONSUMER), Map.class);
            assertThat(res.getStatusCode()).as("filter %s must be 400", q).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).containsEntry("code", "invalid-request");
        }
    }

    @Test
    @DisplayName("current snapshot: consumer reads identity + version + content; drafts never substituted (FR-045)")
    void currentSnapshotShape() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/sops/READ-1", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("sop_id", "READ-1");
        assertThat(((Number) res.getBody().get("version")).intValue()).isEqualTo(1);
        Map<String, Object> content = (Map<String, Object>) res.getBody().get("content");
        assertThat(content).containsEntry("sop_id", "READ-1");
        assertThat(content).containsKeys("title", "policy", "inputs", "rules", "actions", "boundaries", "customer_messages");

        // An unpublished SOP (author has only saved a draft) stays 404 for consumers.
        ResponseEntity<Map> hidden = rest.exchange("/api/v1/sops/HIDDEN-DRAFT", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody()).containsEntry("code", "sop-not-found");
    }

    @Test
    @DisplayName("unknown sop -> 404; unknown version -> 404")
    void missingResourcesAre404() {
        ResponseEntity<Map> res = rest.exchange("/api/v1/sops/NOPE-1", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> v3 = rest.exchange("/api/v1/sops/READ-1/versions/3", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(v3.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(v3.getBody()).containsEntry("code", "version-not-found");
    }
}
