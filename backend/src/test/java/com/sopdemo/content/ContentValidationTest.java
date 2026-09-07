package com.sopdemo.content;

import static com.sopdemo.content.Fixtures.validAnswerDoc;
import static com.sopdemo.content.Fixtures.validRefundDoc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TASK-004/005/006: structural, semantic, and financial-safety validation (unit level, FR-030..034). */
class ContentValidationTest {

    private final ContentEngine engine = new ContentEngine();

    @Test
    void validDocumentCompilesToCanonicalContentInOrder() {
        ContentEngine.ContentAnalysis result = engine.analyze(validRefundDoc());
        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();

        java.util.Map<String, Object> content = result.content();
        assertThat(content.keySet()).containsExactly(
                "sop_id", "title", "owner_team", "domain", "intent", "risk_level",
                "max_autonomy", "policy", "inputs", "rules", "actions", "boundaries",
                "customer_messages");
        assertThat(content.get("sop_id")).isEqualTo("BILL-001");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> policy = (java.util.Map<String, Object>) content.get("policy");
        assertThat(policy).containsKey("do_not_use_when");
        assertThat((java.util.List<?>) content.get("inputs")).hasSize(2);
        assertThat((java.util.List<?>) content.get("rules")).hasSize(2);
        assertThat((java.util.List<?>) content.get("actions")).hasSize(3);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> boundaries = (java.util.Map<String, Object>) content.get("boundaries");
        assertThat(boundaries).containsKey("escalation");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> messages = (java.util.Map<String, Object>) content.get("customer_messages");
        assertThat(messages).containsKey("primary");
    }

    @Test
    void validAnswerQuestionDocumentPasses() {
        assertThat(engine.analyze(validAnswerDoc()).valid()).isTrue();
    }

    @Test
    void missingFrontMatterFieldIsStructural() {
        String src = validRefundDoc().replace("max_autonomy: assist\n", "");
        ContentEngine.ContentAnalysis result = engine.analyze(src);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.MISSING_FIELD);
            assertThat(issue.stage()).isEqualTo(Issue.STAGE_STRUCTURAL);
            assertThat(issue.path()).isEqualTo("frontmatter.max_autonomy");
        });
    }

    @Test
    void unknownFrontMatterFieldIsRejected() {
        String src = validRefundDoc().replace("max_autonomy: assist\n", "max_autonomy: assist\nsecret: x\n");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.UNKNOWN_FIELD));
    }

    @Test
    void invalidDomainEnumIsRejected() {
        String src = validRefundDoc().replace("domain: Billing", "domain: Logistics");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.INVALID_ENUM);
            assertThat(issue.path()).isEqualTo("frontmatter.domain");
        });
    }

    @Test
    void malformedSopIdIsRejected() {
        String src = validRefundDoc().replace("sop_id: BILL-001", "sop_id: bill-001");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.INVALID_ID);
            assertThat(issue.path()).isEqualTo("frontmatter.sop_id");
        });
    }

    @Test
    void nonStringFrontMatterValueIsRejected() {
        String src = validRefundDoc().replace("sop_id: BILL-001", "sop_id: 12345");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isIn(Codes.INVALID_TYPE, Codes.INVALID_ID));
    }

    @Test
    void unknownInputFieldIsRejected() {
        String src = validRefundDoc()
                .replace("- name: customer_verified\n  type: boolean", "- name: customer_verified\n  type: boolean\n  extra: oops");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.UNKNOWN_FIELD));
    }

    @Test
    void booleanInputWithGtIsOperatorMismatch() {
        String src = validRefundDoc().replace("- input: customer_verified\n      op: eq\n",
                "- input: customer_verified\n      op: gt\n");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.OPERATOR_MISMATCH);
            assertThat(issue.stage()).isEqualTo(Issue.STAGE_SEMANTIC);
        });
    }

    @Test
    void duplicateActionIdsAreRejected() {
        String src = validRefundDoc().replace("id: a_escalate", "id: a_refund");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.DUPLICATE_ID);
            assertThat(issue.stage()).isEqualTo(Issue.STAGE_SEMANTIC);
        });
    }

    @Test
    void ruleReferencingUnknownActionIsRejected() {
        String src = validRefundDoc().replace("action_ids:\n    - a_refund", "action_ids:\n    - a_ghost");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.BAD_REFERENCE));
    }

    @Test
    void conditionOnUnknownInputIsRejected() {
        String src = validRefundDoc()
                .replace("- input: customer_verified", "- input: ghost_input");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.BAD_REFERENCE));
    }

    @Test
    void emptyInputsListIsRejectedSemantically() {
        String src = validRefundDoc()
                .replace("""
                          - name: refund_amount
                            type: number
                          - name: customer_verified
                            type: boolean""", "  []");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.EMPTY_REQUIRED_LIST);
            assertThat(issue.stage()).isEqualTo(Issue.STAGE_SEMANTIC);
        });
    }

    @Test
    void maxAmountOnlyAllowedOnRefundActions() {
        String src = validRefundDoc().replace("max_amount: 150", "").replace(
                "description: \"Escalate to the senior billing queue.\"",
                "description: \"Escalate to the senior billing queue.\"\n  max_amount: 10");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.INVALID_TYPE));
    }

    // ---- financial safety (FR-032) ----

    @Test
    void refundIntentRequiringLimitEmitsReadableErrorWhenLimitMissing() {
        String src = validRefundDoc().replace("  max_amount: 150", "");
        ContentEngine.ContentAnalysis result = engine.analyze(src);
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.REFUND_LIMIT_MISSING);
            assertThat(issue.stage()).isEqualTo(Issue.STAGE_SEMANTIC);
            assertThat(issue.message()).isNotBlank();
        });
    }

    @Test
    void refundIntentRequiringEscalationEmitsReadableErrorWhenMissing() {
        String src = validRefundDoc().replace("""
                  escalation:
                    - action_id: a_refund
                      input: refund_amount
                      op: gt
                      amount: 150
                      target_action_id: a_escalate""", "          escalation: []");
        ContentEngine.ContentAnalysis result = engine.analyze(src);
        assertThat(result.issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.REFUND_ESCALATION_MISSING));
    }

    @Test
    void refundIntentEmitsBothDistinctErrorsWhenLimitAndEscalationMissing() {
        String src = validRefundDoc()
                .replace("  max_amount: 150", "")
                .replace("""
                  escalation:
                    - action_id: a_refund
                      input: refund_amount
                      op: gt
                      amount: 150
                      target_action_id: a_escalate""", "          escalation: []");
        ContentEngine.ContentAnalysis result = engine.analyze(src);
        assertThat(result.issues()).extracting(Issue::code)
                .contains(Codes.REFUND_LIMIT_MISSING, Codes.REFUND_ESCALATION_MISSING);
    }

    @Test
    void answerQuestionIntentForbidsRefundActions() {
        String src = validAnswerDoc().replace("kind: human_assist", "kind: refund\n  max_amount: 25");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(Codes.REFUND_NOT_ALLOWED);
            assertThat(issue.message()).contains("answer_question");
        });
    }

    @Test
    void refundIntentOnSupportDomainIsRejected() {
        String src = validRefundDoc().replace("domain: Billing", "domain: Support");
        ContentEngine.ContentAnalysis result = engine.analyze(src);
        // domain enum is valid; the intent/domain cross-check must fire
        assertThat(result.issues()).extracting(Issue::code)
                .contains(Codes.REFUND_INTENT_REQUIRES_BILLING);
    }

    @Test
    void escalationBoundaryTargetMustBeEscalateAction() {
        String src = validRefundDoc().replace("target_action_id: a_escalate", "target_action_id: a_human");
        assertThat(engine.analyze(src).issues()).extracting(Issue::code)
                .containsExactlyInAnyOrder(Codes.BAD_REFERENCE, Codes.REFUND_ESCALATION_MISSING);
    }

    @Test
    void boundaryAmountMustEqualRefundMaxAmount() {
        String src = validRefundDoc().replace("    amount: 150", "    amount: 200");
        assertThat(engine.analyze(src).issues()).anySatisfy(issue ->
                assertThat(issue.code()).isEqualTo(Codes.REFUND_ESCALATION_MISSING));
    }

    @Test
    void issuesAreSortedDeterministicallyByPathThenCode() {
        String src = validRefundDoc()
                .replace("max_autonomy: assist\n", "")
                .replace("domain: Billing", "domain: Logistics")
                .replace("sop_id: BILL-001", "sop_id: bill-001");
        ContentEngine.ContentAnalysis a = engine.analyze(src);
        ContentEngine.ContentAnalysis b = engine.analyze(src);
        assertThat(a.issues()).isEqualTo(b.issues());
        for (int i = 1; i < a.issues().size(); i++) {
            Issue prev = a.issues().get(i - 1);
            Issue cur = a.issues().get(i);
            int cmp = prev.path().compareTo(cur.path());
            if (cmp == 0) {
                cmp = prev.code().compareTo(cur.code());
            }
            assertThat(cmp).isLessThanOrEqualTo(0);
        }
    }

    // ---- size gate (FR-020) ----

    @Test
    void sourceAtExactLimitIsValidAndOverLimitIsRejected() {
        String base = validRefundDoc();
        // Replace the (short) primary message with a padding string tuned to the byte limit.
        int overhead = base.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                - "We spotted the duplicate charge and refunded it.".length();
        int padding = SourceLimits.MAX_SOURCE_BYTES - overhead;
        String atLimit = base.replace("We spotted the duplicate charge and refunded it.",
                "a".repeat(padding));
        assertThat(atLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isEqualTo(SourceLimits.MAX_SOURCE_BYTES);
        assertThat(engine.analyze(atLimit).valid()).isTrue();

        String overLimit = atLimit.replaceFirst("a{5}", "aaaaab");
        assertThat(overLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isEqualTo(SourceLimits.MAX_SOURCE_BYTES + 1);
        assertThatThrownBy(() -> engine.analyze(overLimit))
                .isInstanceOf(SourceTooLargeException.class);
    }
}
