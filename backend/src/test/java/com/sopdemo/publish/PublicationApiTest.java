package com.sopdemo.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 * TASK-009 verification: atomic publication (FR-042), immutable versions
 * (FR-043), failure handling (FR-045), revision conflicts (IR-001 409),
 * role enforcement (FR-001). Uses its own sop_ids so it is order-independent.
 */
class PublicationApiTest extends AbstractPostgresSpringTest {

    private static final String[] IDS = { "PUB-OK", "PUB-INV", "WRONG-1", "PUB-STA", "PUB-DUP", "PUB-CONC", "PUB-V2" };

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
    void clean() {
        for (String id : IDS) {
            jdbc.update("DELETE FROM sop_versions WHERE sop_id = ?", id);
            jdbc.update("DELETE FROM sops WHERE sop_id = ?", id);
            jdbc.update("DELETE FROM drafts WHERE sop_id = ?", id);
        }
    }

    private static String docFor(String sopId, String title) {
        return Fixtures.validRefundDoc()
                .replace("sop_id: BILL-001", "sop_id: " + sopId)
                .replace("title: \"Duplicate Charge Refund\"", "title: \"" + title + "\"");
    }

    private long save(String sopId, String source) {
        ResponseEntity<Map> r = rest.exchange("/api/v1/drafts/" + sopId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("source", source), AUTHOR), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("revision")).longValue();
    }

    private ResponseEntity<Map> publish(String sopId, long revision, HttpHeaders headers) {
        return rest.exchange("/api/v1/sops/" + sopId + "/publish", HttpMethod.POST,
                new HttpEntity<>(Map.of("revision", revision), headers), Map.class);
    }

    @Test
    @DisplayName("valid draft publishes to version 1 and returns the stored canonical snapshot")
    void publishValidDraft() {
        String doc = docFor("PUB-OK", "Duplicate Charge Refund");
        long rev = save("PUB-OK", doc);
        ResponseEntity<Map> res = publish("PUB-OK", rev, AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("sop_id", "PUB-OK");
        assertThat(res.getBody()).containsEntry("version", 1);
        assertThat(res.getBody().get("published_at")).isInstanceOf(String.class); // server-assigned UTC time
        assertThat(res.getBody().get("content")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) res.getBody().get("content");
        assertThat(content).containsEntry("sop_id", "PUB-OK");
        assertThat(content).containsKeys("title", "policy", "inputs", "rules", "actions", "boundaries", "customer_messages");

        // Consumer reads the same snapshot (FR-053 consistency).
        ResponseEntity<Map> consumer = rest.exchange("/api/v1/sops/PUB-OK", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(consumer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consumer.getBody()).containsEntry("version", 1);
        assertThat(consumer.getBody().get("content")).isEqualTo(content);

        // Historical version endpoint (author, FR-043).
        ResponseEntity<Map> v1 = rest.exchange("/api/v1/sops/PUB-OK/versions/1", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(v1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(v1.getBody()).containsEntry("version", 1);
        assertThat(((Number) v1.getBody().get("published_revision")).longValue()).isEqualTo(rev);
        assertThat(((Map<?, ?>) v1.getBody().get("content")).get("sop_id")).isEqualTo("PUB-OK");

        // Consumer cannot read history.
        ResponseEntity<Map> v1c = rest.exchange("/api/v1/sops/PUB-OK/versions/1", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(v1c.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("invalid draft: 422 with issues, failure flag set, nothing published, previous kept")
    void invalidPublishIsRejected422() {
        String broken = docFor("PUB-INV", "Broken Refund")
                .replace("  max_amount: 150", "");
        long rev = save("PUB-INV", broken);
        ResponseEntity<Map> res = publish("PUB-INV", rev, AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).containsEntry("code", "publication-rejected");
        List<?> issues = (List<?>) res.getBody().get("issues");
        assertThat(issues).isNotEmpty();
        assertThat(issues.toString()).contains("refund-limit-missing");

        // FR-045: failure indicator on the draft; consumer detail stays 404.
        ResponseEntity<Map> draft = rest.exchange("/api/v1/drafts/PUB-INV", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(draft.getBody()).containsEntry("publish_failed", true);
        ResponseEntity<Map> consumer = rest.exchange("/api/v1/sops/PUB-INV", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(consumer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(consumer.getBody()).containsEntry("code", "sop-not-found");

        // A fixed save + publish succeeds as version 1.
        long rev2 = save("PUB-INV", docFor("PUB-INV", "Broken Refund"));
        ResponseEntity<Map> ok = publish("PUB-INV", rev2, AUTHOR);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("version", 1);
    }

    @Test
    @DisplayName("source sop_id not matching the draft path -> 422 sop-id-mismatch, nothing published")
    void sopIdMismatchIs422() {
        long rev = save("WRONG-1", Fixtures.validRefundDoc()); // declares BILL-001
        ResponseEntity<Map> res = publish("WRONG-1", rev, AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).containsEntry("code", "sop-id-mismatch");
        Integer versions = jdbc.queryForObject(
                "SELECT count(*) FROM sop_versions WHERE sop_id = 'WRONG-1'", Integer.class);
        assertThat(versions).isZero();
        ResponseEntity<Map> draft = rest.exchange("/api/v1/drafts/WRONG-1", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(draft.getBody()).containsEntry("publish_failed", true);
    }

    @Test
    @DisplayName("publishing a stale revision -> 409; the current version stays unchanged")
    void staleRevisionIs409() {
        long rev1 = save("PUB-STA", docFor("PUB-STA", "Version One"));
        ResponseEntity<Map> first = publish("PUB-STA", rev1, AUTHOR);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        long rev2 = save("PUB-STA", docFor("PUB-STA", "Version Two"));
        assertThat(rev2).isGreaterThan(rev1);
        ResponseEntity<Map> stale = publish("PUB-STA", rev1, AUTHOR);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("code", "stale-revision");

        ResponseEntity<Map> current = rest.exchange("/api/v1/sops/PUB-STA", HttpMethod.GET,
                new HttpEntity<>(CONSUMER), Map.class);
        assertThat(current.getBody()).containsEntry("version", 1);
        assertThat(((Map<?, ?>) current.getBody().get("content")).get("title")).isEqualTo("Version One");

        long rev2Check = save("PUB-STA", docFor("PUB-STA", "Version Two"));
        ResponseEntity<Map> second = publish("PUB-STA", rev2Check, AUTHOR);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).containsEntry("version", 2);
    }

    @Test
    @DisplayName("publishing the same saved revision twice -> second is 409, exactly one version exists")
    void duplicatePublishIs409() {
        long rev = save("PUB-DUP", docFor("PUB-DUP", "Once Only"));
        ResponseEntity<Map> first = publish("PUB-DUP", rev, AUTHOR);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> second = publish("PUB-DUP", rev, AUTHOR);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Integer versions = jdbc.queryForObject(
                "SELECT count(*) FROM sop_versions WHERE sop_id = 'PUB-DUP'", Integer.class);
        assertThat(versions).isEqualTo(1);
    }

    @Test
    @DisplayName("two concurrent publishes of the same revision: exactly one wins (FR-042)")
    void concurrentPublishProducesSingleVersion() throws Exception {
        long rev = save("PUB-CONC", docFor("PUB-CONC", "Concurrent"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map>> call = () -> publish("PUB-CONC", rev, AUTHOR);
            Future<ResponseEntity<Map>> a = pool.submit(call);
            Future<ResponseEntity<Map>> b = pool.submit(call);
            Integer statusA = a.get().getStatusCode().value();
            Integer statusB = b.get().getStatusCode().value();
            assertThat(List.of(statusA, statusB).stream().filter(s -> s == 200).count()).isEqualTo(1);
            assertThat(List.of(statusA, statusB).stream().filter(s -> s == 409).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        Integer versions = jdbc.queryForObject(
                "SELECT count(*) FROM sop_versions WHERE sop_id = 'PUB-CONC'", Integer.class);
        assertThat(versions).isEqualTo(1);
        Integer current = jdbc.queryForObject(
                "SELECT current_version FROM sops WHERE sop_id = 'PUB-CONC'", Integer.class);
        assertThat(current).isEqualTo(1);
        ResponseEntity<Map> v2 = rest.exchange("/api/v1/sops/PUB-CONC/versions/2", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("versions are immutable snapshots; the next publish creates version 2")
    void versionsAreImmutableAndAppend() {
        long rev1 = save("PUB-V2", docFor("PUB-V2", "Original"));
        publish("PUB-V2", rev1, AUTHOR);
        long rev2 = save("PUB-V2", docFor("PUB-V2", "Changed"));
        ResponseEntity<Map> second = publish("PUB-V2", rev2, AUTHOR);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).containsEntry("version", 2);

        ResponseEntity<Map> v1 = rest.exchange("/api/v1/sops/PUB-V2/versions/1", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(((Map<?, ?>) v1.getBody().get("content")).get("title")).isEqualTo("Original");

        ResponseEntity<Map> v2 = rest.exchange("/api/v1/sops/PUB-V2/versions/2", HttpMethod.GET,
                new HttpEntity<>(AUTHOR), Map.class);
        assertThat(((Map<?, ?>) v2.getBody().get("content")).get("title")).isEqualTo("Changed");

        // Old snapshots are untouched (FR-043).
        String stored1 = jdbc.queryForObject(
                "SELECT source FROM sop_versions WHERE sop_id = 'PUB-V2' AND version = 1", String.class);
        assertThat(stored1).contains("title: \"Original\"");
    }

    @Test
    @DisplayName("publishing an unknown draft -> 404 draft-not-found")
    void unknownDraftIs404() {
        ResponseEntity<Map> res = publish("NOPE-9", 1, AUTHOR);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).containsEntry("code", "draft-not-found");
    }

    @Test
    @DisplayName("envelope and role errors on publish")
    void publishEnvelopeAndRoleErrors() {
        HttpHeaders json = new HttpHeaders();
        json.set("X-Demo-User", "demo-author");
        ResponseEntity<Map> noBody =
                rest.exchange("/api/v1/sops/NOPE-9/publish", HttpMethod.POST, new HttpEntity<>(null, json), Map.class);
        assertThat(noBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> missingField =
                rest.exchange("/api/v1/sops/NOPE-9/publish", HttpMethod.POST, new HttpEntity<>(Map.of(), json), Map.class);
        assertThat(missingField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> badType =
                rest.exchange("/api/v1/sops/NOPE-9/publish", HttpMethod.POST,
                        new HttpEntity<>(Map.of("revision", "one"), json), Map.class);
        assertThat(badType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> consumer = publish("NOPE-9", 1, CONSUMER);
        assertThat(consumer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> noId = publish("NOPE-9", 1, new HttpHeaders());
        assertThat(noId.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
