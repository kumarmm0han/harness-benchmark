package com.sopdemo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Draft + identity + seed integration tests against a real PostgreSQL (FR-001, FR-010,
 * DR-001, DR-003). Run with a live DB (see Makefile). Bare `mvn test` excludes this class
 * (default surefire includes are *Test, Test*, *TestCase).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DraftApiIT {

    @Autowired
    MockMvc mvc;

    private static String putBody(String source) {
        try {
            return new ObjectMapper().writeValueAsString(new StringHolder(source));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    record StringHolder(String source) {}

    @Test
    @Order(1)
    void authorCanSaveDraftTwiceWithIncrementingRevision() throws Exception {
        MvcResult r1 = mvc.perform(put("/api/v1/drafts/IT-A")
                        .header("X-Demo-User", "demo-author")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody("first revision of the draft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sop_id").value("IT-A"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.source").value("first revision of the draft"))
                .andReturn();

        MvcResult r2 = mvc.perform(put("/api/v1/drafts/IT-A")
                        .header("X-Demo-User", "demo-author")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody("second revision of the draft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andReturn();

        assertThat(r2.getResponse().getStatus()).isEqualTo(200);

        // Fresh repository view of the same row confirms durability.
        mvc.perform(get("/api/v1/drafts/IT-A").header("X-Demo-User", "demo-author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sop_id").value("IT-A"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.source").value("second revision of the draft"))
                .andExpect(jsonPath("$.publication_failed").value(false));

        // Draft list contains it (membership check; not a global count, so other tests' drafts don't interfere).
        String listBody =
                mvc.perform(get("/api/v1/drafts").header("X-Demo-User", "demo-author"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode hit = null;
        for (var e : new com.fasterxml.jackson.databind.ObjectMapper().readTree(listBody)) {
            if (e.get("sop_id").asText().equals("IT-A")) {
                hit = e;
            }
        }
        assertThat(hit).isNotNull();
        assertThat(hit.get("revision").asLong()).isEqualTo(2);
        assertThat(hit.get("publication_failed").asBoolean()).isFalse();
    }

    @Test
    @Order(2)
    void consumerCannotAccessAuthorEndpoints() throws Exception {
        mvc.perform(put("/api/v1/drafts/IT-B")
                        .header("X-Demo-User", "demo-consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody("should not be stored")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/drafts").header("X-Demo-User", "demo-consumer"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void missingOrUnknownIdentityIsUnauthorized() throws Exception {
        mvc.perform(put("/api/v1/drafts/IT-C")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody("x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(put("/api/v1/drafts/IT-C")
                        .header("X-Demo-User", "intruder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void oversizedSourceIs413() throws Exception {
        String huge = "a".repeat(70_000);
        mvc.perform(put("/api/v1/drafts/IT-D")
                        .header("X-Demo-User", "demo-author")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody(huge)))
                .andExpect(status().isRequestEntityTooLarge())
                .andExpect(jsonPath("$.code").value("SOURCE_TOO_LARGE"));
    }

    @Test
    @Order(5)
    void missingSourceIsBadRequest() throws Exception {
        mvc.perform(put("/api/v1/drafts/IT-E")
                        .header("X-Demo-User", "demo-author")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void missingDraftIs404() throws Exception {
        mvc.perform(get("/api/v1/drafts/not-yet-saved").header("X-Demo-User", "demo-author"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
