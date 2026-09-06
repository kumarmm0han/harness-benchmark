package com.sop.api;

import com.sop.AbstractPostgresTest;
import com.sop.api.Client.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-011 — POST /validate (FR-034, IR-001, NFR-041).
 * Validation returns 200 for valid AND invalid content (200 with issues);
 * invalid requests are 400.
 */
class ValidateApiTest extends AbstractPostgresTest {

  @LocalServerPort
  int port;

  private Client author() {
    return new Client("http://localhost:" + port).as("demo-author");
  }

  @Test
  @DisplayName("POST /validate with a valid source -> 200 valid:true, content non-null")
  void validSource() {
    Response r = author().post("/api/v1/validate", Body.draft(com.sop.domain.Tpl.VALID));
    assertThat(r.status()).isEqualTo(200);
    Json b = Json.of(r.body());
    assertThat(b.bool("valid", false)).isTrue();
    assertThat(b.root().get("content")).isNotNull();
    assertThat(b.root().get("content").get("sop_id")).isNotNull();
  }

  @Test
  @DisplayName("POST /validate with a bad source -> 200 valid:false, content null, non-empty issues (FR-034)")
  void invalidSourceReturnsIssues() {
    String broken = com.sop.domain.Tpl.VALID.replace("domain: Billing", "domain: Marketing");
    Response r = author().post("/api/v1/validate", Body.draft(broken));
    assertThat(r.status()).isEqualTo(200);
    Json b = Json.of(r.body());
    assertThat(b.bool("valid", true)).isFalse();
    // spec §4: invalid content yields content: null
    JsonNode content = b.root().get("content");
    assertThat(content == null || content.isNull()).isTrue();
    assertThat(b.root().get("issues").size()).isGreaterThan(0);
  }

  @Test
  @DisplayName("POST /validate with the missing refund limit + escalation -> 200 with two separate FIN_* issues (AC-E2E-002)")
  void missingLimitAndEscalation() {
    String broken = com.sop.domain.Tpl.VALID
        .replace("  max_amount: 200\n", "")
        .replace("escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2",
                 "escalation: []");
    Response r = author().post("/api/v1/validate", Body.draft(broken));
    assertThat(r.status()).isEqualTo(200);
    Json b = Json.of(r.body());
    assertThat(b.root().get("issues").size()).isGreaterThanOrEqualTo(2);
    // Both problem families must be reported independently.
    String flat = b.root().get("issues").toString();
    assertThat(flat).contains("FIN_REFUND_MAX_AMOUNT");
    assertThat(flat).contains("FIN_ESCALATION_MISSING");
  }

  @Test
  @DisplayName("POST /validate with hostile YAML (alias node) -> 200 valid:false + ALIAS issue (NFR-041 hostile YAML)")
  void hostileAliasRejectedAsIssue() {
    // A YAML alias node (`*dup`) in the Inputs machine section. SafeYaml must
    // refuse to expand it and report a controlled ALIAS issue (FR-020), not
    // crash or execute anything.
    String hostile = com.sop.domain.Tpl.VALID.replace(
        "type: number",
        "type: number\n- name: *dup");
    Response r = author().post("/api/v1/validate", Body.draft(hostile));
    assertThat(r.status()).isEqualTo(200);
    Json b = Json.of(r.body());
    assertThat(b.bool("valid", true)).isFalse();
    JsonNode content = b.root().get("content");
    assertThat(content == null || content.isNull()).isTrue();
    assertThat(b.root().get("issues").toString()).contains("ALIAS");
  }

  @Test
  @DisplayName("POST /validate with no source -> 400")
  void missingSource() {
    Response r = author().post("/api/v1/validate", "{}");
    assertThat(r.status()).isEqualTo(400);
  }

  @Test
  @DisplayName("POST /validate with a blank source -> 400")
  void blankSource() {
    Response r = author().post("/api/v1/validate", Body.draft(""));
    assertThat(r.status()).isEqualTo(400);
  }
}
