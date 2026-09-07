package com.sopdemo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopdemo.domain.model.Published;
import com.sopdemo.persistence.DraftRepository;
import com.sopdemo.persistence.PublicationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Publication + catalog integration tests vs a real PostgreSQL (FR-042/043/045, FR-050/052/053,
 * IR-001): the backend behavior of the five acceptance journeys, atomic + immutable
 * publication, concurrent publishes, and the fixed status contract (400/403/404/409/422).
 *
 * <p>Each journey uses a distinct {@code sop_id} so the classes (and this class) do not
 * interfere in the shared test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationIT {

    private static final String AUTHOR = "demo-author";
    private static final String CONSUMER = "demo-consumer";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PublicationService publicationService;

    @Autowired
    DraftRepository drafts;

    record Src(String source) {}

    // ---- helpers ----

    private String valid(String sopId) {
        try {
            String base = new String(
                    new ClassPathResource("seed/duplicate-charge.md").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return base.replace("sop_id: BILL-REFUND-001", "sop_id: " + sopId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The template with the refund limit and the escalation boundary removed (FR-032 violation). */
    private static final String INVALID_LIMIT_AND_ESCALATION = """
            ---
            sop_id: BILL-B-001
            title: Refund for Duplicate Charge
            owner_team: Billing Operations
            domain: Billing
            intent: refund_duplicate_charge
            risk_level: medium
            max_autonomy: assist
            ---

            ## Intent (When to use)
            - Customer reports a duplicate charge.
            - A support representative confirms the duplicate.

            ## Do Not Use When
            - Fraud is suspected.

            ## Inputs Required
            ```yaml
            - name: refund_amount
              type: number
            - name: duplicate_confirmed
              type: boolean
            ```

            ## Eligibility Rules
            ```yaml
            - id: R1
              conditions:
                - input: duplicate_confirmed
                  op: eq
                  value: true
                - input: refund_amount
                  op: lte
                  value: 200
              action_ids: [A1]
            ```

            ## Actions
            ```yaml
            - id: A1
              kind: refund
              description: A representative may process a confirmed duplicate refund within the limit.
            - id: A2
              kind: escalate
              description: Refer an over-limit request to Billing Support for review.
            ```

            ## Boundaries
            ```yaml
            escalation: []
            ```

            ## Customer Messages
            ```yaml
            primary: A representative can review the confirmed duplicate charge for a refund.
            escalation: This request needs additional review because it exceeds the refund limit.
            ```
            """;

    private long save(String sopId, String source) throws Exception {
        String body =
                mvc.perform(put("/api/v1/drafts/" + sopId)
                                .header("X-Demo-User", AUTHOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new Src(source))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.sop_id").value(sopId))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("revision").asLong();
    }

    private void publish(String sopId, long revision) throws Exception {
        mvc.perform(post("/api/v1/sops/" + sopId + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + revision + "}"))
                .andExpect(status().isOk());
    }

    // ---- AC-E2E-001: author saves, validates, publishes v1; appears in filtered list ----

    @Test
    void journey1_saveValidatePublishAndList() throws Exception {
        String sop = "BILL-A-001";
        String source = valid(sop);
        long rev = save(sop, source);
        assertThat(rev).isPositive();

        mvc.perform(post("/api/v1/validate")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Src(source))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.content.sop_id").value(sop));

        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sop_id").value(sop))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content.title").value("Refund for Duplicate Charge"));

        // Any identity reads the published detail and the filtered summary.
        mvc.perform(get("/api/v1/sops/" + sop).header("X-Demo-User", CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content.sop_id").value(sop));
        String listBody =
                mvc.perform(get("/api/v1/sops")
                                .param("domain", "Billing")
                                .param("risk", "medium")
                                .header("X-Demo-User", CONSUMER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        var hit = findBySopId(listBody, sop);
        assertThat(hit.get("version").asInt()).isEqualTo(1);
        assertThat(hit.get("domain").asText()).isEqualTo("Billing");

        // AND-filter semantics: a non-matching domain excludes it.
        String supportBody =
                mvc.perform(get("/api/v1/sops")
                                .param("domain", "Support")
                                .header("X-Demo-User", CONSUMER))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        for (var e : json.readTree(supportBody)) {
            assertThat(e.get("sop_id").asText()).isNotEqualTo(sop);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode findBySopId(String body, String sopId)
            throws java.io.IOException {
        for (var e : json.readTree(body)) {
            if (e.get("sop_id").asText().equals(sopId)) {
                return e;
            }
        }
        throw new AssertionError(sopId + " not found in list: " + body);
    }

    // ---- AC-E2E-002: financial issues block publish; correcting both allows it ----

    @Test
    void journey2_financialIssuesThenCorrect() throws Exception {
        String sop = "BILL-B-001";
        long revInvalid = save(sop, INVALID_LIMIT_AND_ESCALATION);

        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + revInvalid + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[?(@.code=='FINANCIAL_MISSING_LIMIT')]").isArray())
                .andExpect(jsonPath("$.issues[?(@.code=='FINANCIAL_MISSING_ESCALATION')]").isArray());
        assertThat(drafts.find(sop).orElseThrow().publicationFailed()).isTrue();

        // Correct both and publish again → version 1; indicator cleared.
        long revFixed = save(sop, valid(sop));
        publish(sop, revFixed);
        mvc.perform(get("/api/v1/sops/" + sop).header("X-Demo-User", CONSUMER))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(drafts.find(sop).orElseThrow().publicationFailed()).isFalse();
    }

    // ---- AC-E2E-003: consumer reads the published snapshot; consumer mutation → 403 ----

    @Test
    void journey3_consumerReadsAndCannotMutate() throws Exception {
        String sop = "BILL-C-001";
        publish(sop, save(sop, valid(sop)));

        // Consumer reads current snapshot (identity + version + policy).
        mvc.perform(get("/api/v1/sops/" + sop).header("X-Demo-User", CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sop_id").value(sop))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content.sop_id").value(sop));

        // Consumer mutation attempts → 403.
        mvc.perform(put("/api/v1/drafts/" + sop)
                        .header("X-Demo-User", CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Src("x"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---- AC-E2E-004: failed publish retains v1; corrected publish → v2; v1 unchanged ----

    @Test
    void journey4_failedPublishRetainsV1ThenPublishesV2() throws Exception {
        String sop = "BILL-D-001";
        publish(sop, save(sop, valid(sop))); // v1

        // Invalid replacement (drops the refund limit) → publish fails, v1 retained.
        String invalid = valid(sop).replace("  max_amount: 200\n", "");
        long rev = save(sop, invalid);
        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev + "}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/sops/" + sop).header("X-Demo-User", CONSUMER))
                .andExpect(jsonPath("$.version").value(1));

        // Corrected source → v2; v1 remains retrievable and unchanged.
        publish(sop, save(sop, valid(sop)));
        mvc.perform(get("/api/v1/sops/" + sop).header("X-Demo-User", CONSUMER))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/v1/sops/" + sop + "/versions/1").header("X-Demo-User", AUTHOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content.sop_id").value(sop))
                .andExpect(jsonPath("$.content.actions[?(@.id=='A1')].max_amount").value(200.0));
    }

    // ---- status contract: 404 / 400 / 409 / 422 ----

    @Test
    void absentIs404() throws Exception {
        mvc.perform(get("/api/v1/sops/NO-SUCH-SOP").header("X-Demo-User", CONSUMER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/sops/BILL-D-001/versions/99").header("X-Demo-User", AUTHOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidFilterAndMissingSourceAre400() throws Exception {
        mvc.perform(get("/api/v1/sops").param("domain", "Bogus").header("X-Demo-User", CONSUMER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/sops").param("risk", "high").header("X-Demo-User", CONSUMER))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/validate")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleRevisionIs409() throws Exception {
        String sop = "BILL-E-001";
        long rev1 = save(sop, valid(sop));
        publish(sop, rev1);
        long rev2 = save(sop, valid(sop)); // draft now at revision 2
        assertThat(rev2).isGreaterThan(1);
        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev1 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void duplicateRevisionIs409() throws Exception {
        String sop = "BILL-F-001";
        long rev = save(sop, valid(sop));
        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // Same draft revision again (draft still at `rev`) → duplicate publication.
        mvc.perform(post("/api/v1/sops/" + sop + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void sopIdMismatchIs422() throws Exception {
        String path = "BILL-G-001";
        long rev = save(path, valid("DIFFERENT-SOP"));
        mvc.perform(post("/api/v1/sops/" + path + "/publish")
                        .header("X-Demo-User", AUTHOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":" + rev + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[?(@.code=='SOP_ID_MISMATCH')]").isArray());
    }

    // ---- concurrency: racing the same saved revision must not yield conflicting currents ----

    @Test
    void concurrentPublishesOfSameRevisionOnlyOneSucceeds() throws Exception {
        String sop = "BILL-H-001";
        long rev = save(sop, valid(sop));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Published> t1 = () -> publicationService.publish(sop, rev);
            Callable<Published> t2 = () -> publicationService.publish(sop, rev);
            var f1 = pool.submit(t1);
            var f2 = pool.submit(t2);
            int wins = 0;
            for (var f : List.of(f1, f2)) {
                try {
                    f.get();
                    wins++;
                } catch (ExecutionException expected409) {
                    // loser sees a 409 conflict (duplicate revision)
                }
            }
            assertThat(wins).isEqualTo(1);
            Published current = publicationService.currentPublished(sop);
            assertThat(current).isNotNull();
            assertThat(current.version()).isEqualTo(1);
            assertThat(current.content().sopId()).isEqualTo(sop);
        } finally {
            pool.shutdownNow();
        }
    }
}
