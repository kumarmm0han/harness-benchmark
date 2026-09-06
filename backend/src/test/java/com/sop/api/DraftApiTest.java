package com.sop.api;

import com.sop.AbstractPostgresTest;
import com.sop.api.Client.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-009 — draft save/get/list (FR-010, IR-001) with PostgreSQL.
 */
class DraftApiTest extends AbstractPostgresTest {

  @LocalServerPort
  int port;

  private Client author() {
    return new Client("http://localhost:" + port).as("demo-author");
  }

  @Test
  @DisplayName("PUT /drafts/{id} -> revision 1 on first save, 2 on second save, source preserved")
  void saveIncrementsRevisionAndPreservesSource() {
    Client a = author();
    String sop = "DRAFT-INC-001";

    Response r1 = a.put("/api/v1/drafts/" + sop, Body.draft(com.sop.domain.Tpl.VALID));
    assertThat(r1.status()).isEqualTo(200);
    Json b1 = Json.of(r1.body());
    assertThat(b1.intv("revision", -1)).isEqualTo(1);
    assertThat(b1.text("source")).isEqualTo(com.sop.domain.Tpl.VALID);
    assertThat(b1.text("sop_id")).isEqualTo(sop);

    String edited = com.sop.domain.Tpl.VALID.replace("Refund for Duplicate Charge", "Refund for Duplicate Charge v2");
    Response r2 = a.put("/api/v1/drafts/" + sop, Body.draft(edited));
    assertThat(r2.status()).isEqualTo(200);
    Json b2 = Json.of(r2.body());
    assertThat(b2.intv("revision", -1)).isEqualTo(2);
    assertThat(b2.text("source")).isEqualTo(edited);
  }

  @Test
  @DisplayName("GET /drafts/{id} returns saved source, revision, and no failure indicator")
  void getSavedDraft() {
    Client a = author();
    String sop = "DRAFT-GET-001";
    a.put("/api/v1/drafts/" + sop, Body.draft(com.sop.domain.Tpl.VALID));

    Response r = a.get("/api/v1/drafts/" + sop);
    assertThat(r.status()).isEqualTo(200);
    assertThat(Json.of(r.body()).text("sop_id")).isEqualTo(sop);
    assertThat(Json.of(r.body()).text("source")).isEqualTo(com.sop.domain.Tpl.VALID);
    assertThat(Json.of(r.body()).bool("publish_failed", true)).isFalse();
  }

  @Test
  @DisplayName("GET /drafts lists all saved drafts; consumer sees 403")
  void listDrafts() {
    Client a = author();
    a.put("/api/v1/drafts/" + "DRAFT-LIST-001", Body.draft(com.sop.domain.Tpl.VALID));
    a.put("/api/v1/drafts/" + "DRAFT-LIST-002", Body.draft(com.sop.domain.Tpl.VALID));

    String body = a.get("/api/v1/drafts").body();
    Json arr = Json.of(body);
    assertThat(arr.root().isArray()).isTrue();
    assertThat(arr.root().size()).isGreaterThanOrEqualTo(2);

    Response consumer = new Client("http://localhost:" + port).as("demo-consumer")
        .get("/api/v1/drafts");
    assertThat(consumer.status()).isEqualTo(403);
  }

  @Test
  @DisplayName("GET /drafts/{id} for an absent draft -> 404")
  void draftNotFound() {
    Response r = author().get("/api/v1/drafts/NEVER-EXISTS");
    assertThat(r.status()).isEqualTo(404);
  }

  @Test
  @DisplayName("PUT /drafts/{id} with >64 KiB source -> 413")
  void oversizedSourceRejected() {
    // 70,000 'x' chars in a string value => > 65,536 UTF-8 bytes.
    StringBuilder s = new StringBuilder("---\nsop_id: OVERSIZED-001\ntitle: Big\nowner_team: X\ndomain: Billing\nintent: answer_question\nrisk_level: low\nmax_autonomy: assist\n---\n## Intent (When to use)\n- ");
    for (int i = 0; i < 70_000; i++) s.append('x');
    s.append("\n\n## Do Not Use When\n- x\n\n## Inputs Required\n```yaml\n- name: a\n  type: number\n```\n\n## Eligibility Rules\n```yaml\n- id: R1\n  conditions:\n    - input: a\n      op: eq\n      value: 1\n  action_ids: [A1]\n```\n\n## Actions\n```yaml\n- id: A1\n  kind: human_assist\n  description: x\n```\n\n## Boundaries\n```yaml\nescalation: []\n```\n\n## Customer Messages\n```yaml\nprimary: x\nescalation: y\n```\n");
    Response r = author().put("/api/v1/drafts/OVERSIZED-001", Body.draft(s.toString()));
    assertThat(r.status()).isEqualTo(413);
    assertThat(Json.of(r.body()).text("code")).isEqualTo("SOURCE_TOO_LARGE");
  }
}
