package com.sop.bootstrap;

import com.sop.AbstractPostgresTest;
import com.sop.api.Body;
import com.sop.api.Client;
import com.sop.api.Client.Response;
import com.sop.api.Json;
import com.sop.domain.DraftRepository;
import com.sop.service.SeedTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-014 — deterministic demo seed (DR-003, AC-E2E-005, DES-010).
 * SeedInitializer is profile-gated (`demo`) with an empty-table guard:
 * - first start inserts the spec §3 template as a saved draft (revision 1);
 * - a re-run never duplicates or overwrites user edits;
 * - it never creates a publication (consumer detail 404 until published).
 *
 * These tests exercise SeedInitializer directly (the same bean the demo
 * profile boots) plus a real HTTP consumer check that seed alone publishes
 * nothing.
 */
class SeedInitializerTest extends AbstractPostgresTest {

  @LocalServerPort
  int port;

  @Autowired
  DraftRepository drafts;

  private Client author() {
    return new Client("http://localhost:" + port).as("demo-author");
  }

  private Client consumer() {
    return new Client("http://localhost:" + port).as("demo-consumer");
  }

  @Test
  @DisplayName("first seed inserts draft BILL-REFUND-001 at revision 1 with the exact spec §3 source")
  void firstSeedInsertsVerbatimDraft() {
    new SeedInitializer(drafts).run(null);

    Integer rev = jdbc.queryForObject(
        "select revision from drafts where sop_id = 'BILL-REFUND-001'",
        Integer.class);
    assertThat(rev).isEqualTo(1);

    String source = jdbc.queryForObject(
        "select source from drafts where sop_id = 'BILL-REFUND-001'",
        String.class);
    assertThat(source).isEqualTo(SeedTemplate.BILLING_REFUND);
    assertThat(source).contains("sop_id: BILL-REFUND-001");
    assertThat(source).contains("max_amount: 200");
  }

  @Test
  @DisplayName("a second seed run is a no-op (no duplicate rows, draft unchanged)")
  void secondSeedIsNoOp() {
    SeedInitializer initializer = new SeedInitializer(drafts);
    initializer.run(null);
    int countAfterFirst = countDrafts();

    initializer.run(null);

    assertThat(countDrafts()).isEqualTo(countAfterFirst);
    Integer rev = jdbc.queryForObject(
        "select revision from drafts where sop_id = 'BILL-REFUND-001'",
        Integer.class);
    assertThat(rev).isEqualTo(1);
    // Seed creates no publications.
    Integer pub = jdbc.queryForObject("select count(*) from publications", Integer.class);
    assertThat(pub).isZero();
  }

  @Test
  @DisplayName("after the author edits and saves the draft, a re-seed does not overwrite the edit")
  void reseedDoesNotClobberAuthorEdits() {
    new SeedInitializer(drafts).run(null);

    Client a = author();
    String edited = SeedTemplate.BILLING_REFUND.replace(
        "Refund for Duplicate Charge", "Refund for Duplicate Charge (edited)");
    Response saved = a.put("/api/v1/drafts/BILL-REFUND-001", Body.draft(edited));
    assertThat(saved.status()).isEqualTo(200);
    assertThat(Json.of(saved.body()).intv("revision", -1)).isEqualTo(2);

    new SeedInitializer(drafts).run(null);

    String current = jdbc.queryForObject(
        "select source from drafts where sop_id = 'BILL-REFUND-001'",
        String.class);
    assertThat(current).isEqualTo(edited);
    Integer rev = jdbc.queryForObject(
        "select revision from drafts where sop_id = 'BILL-REFUND-001'",
        Integer.class);
    assertThat(rev).isEqualTo(2);
  }

  @Test
  @DisplayName("seed never creates a publication: consumer detail is 404 until the author publishes (FR-045)")
  void seedPublishesNothing() {
    new SeedInitializer(drafts).run(null);

    Response r = consumer().get("/api/v1/sops/BILL-REFUND-001");
    assertThat(r.status()).isEqualTo(404);

    // And the author can complete the journey from the seeded draft (AC-E2E-001).
    Client a = author();
    assertThat(a.post("/api/v1/sops/BILL-REFUND-001/publish", Body.revision(1)).status())
        .isEqualTo(200);
    assertThat(consumer().get("/api/v1/sops/BILL-REFUND-001").status()).isEqualTo(200);
  }

  private int countDrafts() {
    Integer n = jdbc.queryForObject("select count(*) from drafts", Integer.class);
    return n == null ? 0 : n;
  }
}
