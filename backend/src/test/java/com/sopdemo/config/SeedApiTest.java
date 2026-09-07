package com.sopdemo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sopdemo.AbstractPostgresSpringTest;

/**
 * TASK-010 verification (DR-003, FR-004): the demo profile seeds the
 * duplicate-charge example as a saved draft; repeated startups neither
 * duplicate records nor overwrite author edits.
 */
@ActiveProfiles("demo")
class SeedApiTest extends AbstractPostgresSpringTest {

    private static final String SOP = "BILL-REFUND-001";
    private static final HttpHeaders AUTHOR = new HttpHeaders();

    static {
        AUTHOR.set("X-Demo-User", "demo-author");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SeedInitializer seed;

    @BeforeAll
    void resetToFirstBoot() {
        jdbc.update("DELETE FROM sop_versions WHERE sop_id = ?", SOP);
        jdbc.update("DELETE FROM sops WHERE sop_id = ?", SOP);
        jdbc.update("DELETE FROM drafts WHERE sop_id = ?", SOP);
        seed.run(null); // deterministic "first boot"
    }

    private static String template() throws IOException {
        return new String(new ClassPathResource("seed/duplicate-charge.md").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("demo seed creates the saved draft with the spec template (DR-003)")
    void seedCreatesDraftOnly() throws IOException {
        ResponseEntity<Map> d = rest.exchange("/api/v1/drafts/" + SOP, HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(d.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) d.getBody().get("revision")).intValue()).isEqualTo(1);
        assertThat(d.getBody().get("source")).isEqualTo(template());
        assertThat(d.getBody()).containsEntry("publish_failed", false);

        // Seed is draft-only: nothing published yet (consumer list/detail must not show it).
        Integer versions = jdbc.queryForObject(
                "SELECT count(*) FROM sop_versions WHERE sop_id = ?", Integer.class, SOP);
        assertThat(versions).isZero();
        ResponseEntity<Map> detail = rest.exchange("/api/v1/sops/" + SOP, HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("repeated startups never duplicate records nor clobber author edits (DR-003)")
    void reseedIsIdempotentAndEditSafe() throws IOException {
        // Author edits the draft (second revision).
        String edited = template().replace("sop_id: BILL-REFUND-001", "sop_id: BILL-REFUND-001")
                .replace("owner_team: Billing Operations", "owner_team: Billing Ops Squad");
        ResponseEntity<Map> put = rest.exchange("/api/v1/drafts/" + SOP, HttpMethod.PUT,
                new HttpEntity<>(Map.of("source", edited), AUTHOR), Map.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) put.getBody().get("revision")).intValue()).isEqualTo(2);

        // "Restart" the app twice more (DR-003: repeated startups).
        seed.run(null);
        seed.run(null);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM drafts WHERE sop_id = ?", Integer.class, SOP);
        assertThat(count).isEqualTo(1);
        ResponseEntity<Map> d = rest.exchange("/api/v1/drafts/" + SOP, HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(((Number) d.getBody().get("revision")).intValue()).isEqualTo(2);
        assertThat(d.getBody().get("source")).isEqualTo(edited); // not reset to the template

        // And on a genuinely missing draft, one more boot fills it in again.
        jdbc.update("DELETE FROM drafts WHERE sop_id = ?", SOP);
        seed.run(null);
        ResponseEntity<Map> again = rest.exchange("/api/v1/drafts/" + SOP, HttpMethod.GET, new HttpEntity<>(AUTHOR), Map.class);
        assertThat(((Number) again.getBody().get("revision")).intValue()).isEqualTo(1);
        assertThat(again.getBody().get("source")).isEqualTo(template());
    }
}
