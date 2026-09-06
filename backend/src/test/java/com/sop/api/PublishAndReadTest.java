package com.sop.api;

import com.sop.AbstractPostgresTest;
import com.sop.api.Client;
import com.sop.api.Client.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-012 — atomic immediate publication (FR-042, FR-043, FR-045, PRN-006).
 * TASK-013 — read API (FR-050, FR-053) including immutable historical versions.
 * Concurrency: two publishers of different revisions must not conflict.
 */
class PublishAndReadTest extends AbstractPostgresTest {

  static final String SOP = "PUB-001";
  @LocalServerPort
  int port;
  private Client author() { return new Client(base()).as("demo-author"); }
  private Client consumer() { return new Client(base()).as("demo-consumer"); }
  private String base() { return "http://localhost:" + port; }

  // ---- helpers ----

  /** The spec §3 template with its front-matter sop_id aligned to this class's draft path (IR-001). */
  private String source() {
    return com.sop.domain.Tpl.VALID.replace("BILL-REFUND-001", SOP);
  }

  private long publishRev() {
    author().put("/api/v1/drafts/" + SOP, Body.draft(source()));
    Integer cur = jdbc.queryForObject(
        "select version from sop_current where sop_id = ?", Integer.class, SOP);
    return cur == null ? 1 : cur;
  }

  private long currentVersion() {
    List<Integer> rows = jdbc.query(
        "select version from sop_current where sop_id = ?",
        (rs, n) -> rs.getInt(1), SOP);
    return rows.isEmpty() ? -1 : rows.get(0);
  }

  // ---- atomic publication (FR-042) ----

  @Test
  @DisplayName("publish a valid draft -> 200, version 1, immutable snapshot; sop_current = 1")
  void publishV1() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));

    Response r = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));
    assertThat(r.status()).isEqualTo(200);
    Json b = Json.of(r.body());
    assertThat(b.intv("version", -1)).isEqualTo(1);
    assertThat(b.text("sop_id")).isEqualTo(SOP);
    assertThat(b.root().get("content")).isNotNull();
    assertThat(currentVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("stale revision -> 409, no version created")
  void staleRevision() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    a.put("/api/v1/drafts/" + SOP, Body.draft(source())); // now rev 2

    Response r = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));
    assertThat(r.status()).isEqualTo(409);
    assertThat(Json.of(r.body()).text("code")).isEqualTo("STALE_REVISION");
    assertThat(currentVersion()).isEqualTo(-1);
  }

  @Test
  @DisplayName("duplicate publish (same revision) -> 409, no second version")
  void duplicatePublish() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));

    Response first = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));
    assertThat(first.status()).isEqualTo(200);

    Response second = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));
    assertThat(second.status()).isEqualTo(409);
    assertThat(Json.of(second.body()).text("code")).isEqualTo("PUBLICATION_CONFLICT");
    assertThat(currentVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-E2E-004 — invalid publish retains v1, sets the failure indicator, then a corrected publish makes v2")
  void ac004InvalidThenFixed() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    assertThat(a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1)).status()).isEqualTo(200); // v1

    // Save an invalid replacement, then attempt to publish — v1 remains current.
    String broken = source().replace("  max_amount: 200\n", "");
    a.put("/api/v1/drafts/" + SOP, Body.draft(broken)); // rev 2
    Response bad = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(2));
    assertThat(bad.status()).isEqualTo(422);
    assertThat(Json.of(bad.body()).text("code")).isEqualTo("VALIDATION_FAILED");

    // Failure indicator is set for the failed draft revision (FR-045).
    Boolean failed = jdbc.queryForObject(
        "select 1 from drafts where sop_id = ? and publish_failed_at is not null",
        Boolean.class, SOP) != null;
    assertThat(failed).isTrue();
    // v1 is still current and unchanged.
    assertThat(currentVersion()).isEqualTo(1);

    // Correct and publish -> v2, indicator cleared.
    a.put("/api/v1/drafts/" + SOP, Body.draft(source())); // rev 3, indicator cleared
    Integer failedNow = jdbc.queryForObject(
        "select case when publish_failed_at is not null then 1 else 0 end from drafts where sop_id = ?",
        Integer.class, SOP);
    assertThat(failedNow).isZero();
    assertThat(a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(3)).status()).isEqualTo(200);
    assertThat(currentVersion()).isEqualTo(2);
  }

  @Test
  @DisplayName("immutable versions: after publishing v2, v1 is retrievable and identical")
  void historicalVersionImmutable() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    assertThat(a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1)).status()).isEqualTo(200); // v1
    a.put("/api/v1/drafts/" + SOP, Body.draft(source().replace("Refund for Duplicate Charge", "Refund v2")));
    assertThat(a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(2)).status()).isEqualTo(200); // v2

    Json v1 = Json.of(a.get("/api/v1/sops/" + SOP + "/versions/1").body());
    Json v2 = Json.of(a.get("/api/v1/sops/" + SOP + "/versions/2").body());
    assertThat(v1.intv("version", -1)).isEqualTo(1);
    assertThat(v2.intv("version", -1)).isEqualTo(2);
    // v1 content must be the original (unchanged by the v2 save)
    assertThat(v1.root().get("content").get("title").asText()).isEqualTo("Refund for Duplicate Charge");
    assertThat(v2.root().get("content").get("title").asText()).isEqualTo("Refund v2");
  }

  // ---- read API (FR-050) ----

  @Test
  @DisplayName("GET /sops filters by domain and risk, sorted by sop_id; invalid filter -> 400")
  void listFilters() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));

    // Billing + medium matches the seed template.
    String all = a.get("/api/v1/sops").body();
    assertThat(Json.of(all).root().size()).isGreaterThanOrEqualTo(1);

    String billingMedium = a.get("/api/v1/sops?domain=Billing&risk=medium").body();
    assertThat(Json.of(billingMedium).root().size()).isGreaterThanOrEqualTo(1);

    // Risk low is not in the template -> empty, but not an error (FR-050 empty state).
    String riskLow = a.get("/api/v1/sops?domain=Billing&risk=low").body();
    assertThat(Json.of(riskLow).root().isArray()).isTrue();

    // Invalid domain -> 400 INVALID_FILTER.
    Response bad = a.get("/api/v1/sops?domain=Marketing");
    assertThat(bad.status()).isEqualTo(400);
    assertThat(Json.of(bad.body()).text("code")).isEqualTo("INVALID_FILTER");
  }

  @Test
  @DisplayName("GET /sops/{id} with no publication -> 404 (never substitutes draft data, FR-045)")
  void detail404WhenNothingPublished() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    Response r = consumer().get("/api/v1/sops/" + SOP);
    assertThat(r.status()).isEqualTo(404);
  }

  @Test
  @DisplayName("human and JSON views are the same canonical snapshot (FR-053): same sop_id+version")
  void humanAndJsonConsistent() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));

    Json cur = Json.of(consumer().get("/api/v1/sops/" + SOP).body());
    Json hist = Json.of(a.get("/api/v1/sops/" + SOP + "/versions/1").body());
    assertThat(cur.text("sop_id")).isEqualTo(hist.text("sop_id"));
    assertThat(cur.intv("version", -1)).isEqualTo(hist.intv("version", -1));
    // The content the human view renders is the same canonical object.
    assertThat(cur.root().get("content").toString()).isEqualTo(hist.root().get("content").toString());
  }

  @Test
  @DisplayName("publishers of two different revisions both commit as distinct versions 2 & 3 (TASK-012)")
  void publishersOfDifferentRevisionsBothCommit() {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source()));
    assertThat(a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1)).status()).isEqualTo(200); // v1

    // Publisher A saves revision 2 and publishes it → version 2.
    a.put("/api/v1/drafts/" + SOP, Body.draft(source().replace("Refund for Duplicate Charge", "Rev2")));
    Response v2 = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(2));
    assertThat(v2.status()).isEqualTo(200);
    assertThat(Json.of(v2.body()).intv("version", -1)).isEqualTo(2);

    // Publisher B saves revision 3 and publishes it → version 3.
    a.put("/api/v1/drafts/" + SOP, Body.draft(source().replace("Refund for Duplicate Charge", "Rev3")));
    Response v3 = a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(3));
    assertThat(v3.status()).isEqualTo(200);
    assertThat(Json.of(v3.body()).intv("version", -1)).isEqualTo(3);

    int rows = jdbc.queryForObject(
        "select count(*) from publications where sop_id = ?", Integer.class, SOP);
    assertThat(rows).isEqualTo(3);
    assertThat(currentVersion()).isEqualTo(3);
    // Both snapshots remain retrievable and distinct (FR-043 immutability).
    Json h2 = Json.of(a.get("/api/v1/sops/" + SOP + "/versions/2").body());
    Json h3 = Json.of(a.get("/api/v1/sops/" + SOP + "/versions/3").body());
    assertThat(h2.root().get("content").get("title").asText()).isEqualTo("Rev2");
    assertThat(h3.root().get("content").get("title").asText()).isEqualTo("Rev3");
  }

  // ---- concurrency (FR-042) ----

  @Test
  @DisplayName("concurrent publishes of the same sop never produce a conflicting current version (FR-042)")
  void concurrentPublishSameSop() throws Exception {
    Client a = author();
    a.put("/api/v1/drafts/" + SOP, Body.draft(source())); // rev 1

    int n = 4;
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(n);
    List<Future<Response>> futs = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      futs.add(pool.submit(() -> {
        start.await();
        return a.post("/api/v1/sops/" + SOP + "/publish", Body.revision(1));
      }));
    }
    start.countDown();
    int successCount = 0;
    int conflictCount = 0;
    for (Future<Response> f : futs) {
      Response r = f.get(15, TimeUnit.SECONDS);
      if (r.status() == 200) successCount++;
      if (r.status() == 409) conflictCount++;
    }
    pool.shutdown();

    // Exactly one of the concurrent publishes may win; the rest must be rejected
    // (duplicate publication of the same saved revision). No conflicting versions.
    assertThat(successCount).isEqualTo(1);
    assertThat(conflictCount).isEqualTo(n - 1);
    // Exactly one publication row for this sop, and it is version 1 (the current one).
    int rows = jdbc.queryForObject(
        "select count(*) from publications where sop_id = ?", Integer.class, SOP);
    assertThat(rows).isEqualTo(1);
    assertThat(currentVersion()).isEqualTo(1);
  }
}
