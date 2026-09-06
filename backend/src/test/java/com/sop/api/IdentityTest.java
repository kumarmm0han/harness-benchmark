package com.sop.api;

import com.sop.AbstractPostgresTest;
import com.sop.api.Client.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-010 — identity enforcement (FR-001, NFR-020, PRN-004).
 * Backend checks the identity on every request; missing/unknown => 401;
 * consumer on an author-only op => 403.
 */
class IdentityTest extends AbstractPostgresTest {

  @LocalServerPort
  int port;

  private Client client(String identity) {
    return new Client("http://localhost:" + port).as(identity);
  }

  @Test
  @DisplayName("missing identity header -> 401")
  void missingIdentity() {
    Response r = new Client("http://localhost:" + port).get("/api/v1/sops");
    assertThat(r.status()).isEqualTo(401);
    assertThat(Json.of(r.body()).text("code")).isEqualTo("MISSING_IDENTITY");
  }

  @Test
  @DisplayName("unknown identity -> 401")
  void unknownIdentity() {
    Response r = client("someone-else").get("/api/v1/sops");
    assertThat(r.status()).isEqualTo(401);
  }

  @Test
  @DisplayName("consumer on author-only ops -> 403 (validate / save / list-drafts / get-draft / publish / getVersion)")
  void consumerForbiddenOnAuthorOps() {
    String sop = "IDENT-FORBIDDEN-001";
    Client consumer = client("demo-consumer");
    Client author = client("demo-author");
    author.put("/api/v1/drafts/" + sop, Body.draft(com.sop.domain.Tpl.VALID.replace("BILL-REFUND-001", sop)));

    assertThat(consumer.post("/api/v1/validate", Body.draft(com.sop.domain.Tpl.VALID)).status())
        .isEqualTo(403);
    assertThat(consumer.put("/api/v1/drafts/" + sop, Body.draft(com.sop.domain.Tpl.VALID)).status())
        .isEqualTo(403);
    assertThat(consumer.get("/api/v1/drafts").status()).isEqualTo(403);
    assertThat(consumer.get("/api/v1/drafts/" + sop).status()).isEqualTo(403);
    assertThat(consumer.post("/api/v1/sops/" + sop + "/publish", Body.revision(1)).status())
        .isEqualTo(403);
    // historical version is author-only (publish first as author)
    assertThat(author.post("/api/v1/sops/" + sop + "/publish", Body.revision(1)).status()).isEqualTo(200);
    assertThat(consumer.get("/api/v1/sops/" + sop + "/versions/1").status()).isEqualTo(403);
  }

  @Test
  @DisplayName("consumer CAN read published list/detail (either role)")
  void consumerCanReadPublished() {
    String sop = "IDENT-READ-001";
    Client author = client("demo-author");
    Client consumer = client("demo-consumer");
    author.put("/api/v1/drafts/" + sop, Body.draft(com.sop.domain.Tpl.VALID.replace("BILL-REFUND-001", sop)));
    assertThat(author.post("/api/v1/sops/" + sop + "/publish", Body.revision(1)).status()).isEqualTo(200);

    assertThat(consumer.get("/api/v1/sops").status()).isEqualTo(200);
    assertThat(consumer.get("/api/v1/sops/" + sop).status()).isEqualTo(200);
  }
}
