package com.sop.domain;

import com.sop.dto.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sop.domain.Tpl.VALID;
import static com.sop.domain.Tpl.hasCode;
import static com.sop.domain.Tpl.validate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-007 — financial safety validation (FR-032, PRN-005, NFR-041).
 *
 * <p>The refund intent requires: exactly one refund action with a positive
 * finite max_amount; a refund_amount number input; and at least one escalation
 * boundary naming that action, using refund_amount, op gt, amount == max_amount,
 * targeting an escalate action. Each failure is reported independently.
 */
class FinancialValidationTest {

  private static String replace(String haystack, String needle, String replacement) {
    assertThat(haystack).contains(needle);
    return haystack.replace(needle, replacement);
  }

  @Test
  @DisplayName("the valid template has no FIN_* issues")
  void validTemplateNoFinancialIssues() {
    assertThat(ValidatorPipeline.validate(VALID).issues())
        .noneMatch(i -> i.code().startsWith("FIN_") || i.code().equals("REFUND_ACTION_MULTIPLE"));
  }

  @Nested
  @DisplayName("refund duplicate-charge checks")
  class RefundChecks {

    @Test
    void missingRefundAction() {
      // Remove the A1 refund action entirely.
      String broken = VALID.replace(
          "- id: A1\n  kind: refund\n  description: A representative may process a confirmed duplicate refund within the limit.\n  max_amount: 200\n",
          "");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_REFUND_MISSING")).as("missing refund action is flagged").isTrue();
    }

    @Test
    void missingMaxAmount() {
      String broken2 = VALID.replace("  max_amount: 200\n", "");
      List<Issue> issues = validate(broken2);
      assertThat(hasCode(issues, "FIN_REFUND_MAX_AMOUNT")).as("missing max_amount is flagged").isTrue();
    }

    @Test
    void zeroMaxAmount() {
      String broken = replace(VALID, "max_amount: 200", "max_amount: 0");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_REFUND_MAX_AMOUNT")).as("zero limit is flagged").isTrue();
    }

    @Test
    void missingRefundAmountInput() {
      String broken = VALID.replace("- name: refund_amount\n  type: number\n", "");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_REFUND_AMOUNT_INPUT")).as("missing refund_amount input is flagged").isTrue();
    }

    @Test
    void missingEscalation() {
      // Empty the Boundaries escalation list → no escalation boundary remains.
      String broken = VALID.replace(
          "escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2",
          "escalation: []");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_ESCALATION_MISSING")).as("missing escalation is flagged").isTrue();
    }

    @Test
    void amountNotEqualToLimit() {
      // Escalation amount (200) no longer equals the refund limit (300).
      String broken = replace(VALID, "max_amount: 200", "max_amount: 300");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_ESCALATION_BOUND")).as("mismatched escalation amount is flagged").isTrue();
    }

    @Test
    void multipleRefundActions() {
      // Add a second refund action; it must be flagged.
      String broken = VALID
          .replace("- id: A2\n  kind: escalate\n  description: Refer an over-limit request to Billing Support for review.",
                  "- id: A2\n  kind: escalate\n  description: Refer an over-limit request to Billing Support for review.\n- id: A3\n  kind: refund\n  description: another refund\n  max_amount: 10");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "REFUND_ACTION_MULTIPLE")).as("a second refund action is flagged").isTrue();
    }

    @Test
    @DisplayName("AC-E2E-002 — removing both limit and escalation yields two distinct readable issues")
    void ac002SeparateReadableIssues() {
      // Remove the refund limit AND the escalation boundary.
      String broken = VALID.replace("  max_amount: 200\n", "");
      broken = broken.replace("escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2\n",
                             "escalation: []\n");
      List<Issue> issues = validate(broken);
      // Both problems must be reported independently (PRN-005 "separate readable errors").
      assertThat(hasCode(issues, "FIN_REFUND_MAX_AMOUNT")).isTrue();
      assertThat(hasCode(issues, "FIN_ESCALATION_MISSING")).isTrue();
      // Every issue carries a code, a stage, a message and a path so the UI can point
      // to the affected field (FR-034).
      issues.forEach(i -> {
        assertThat(i.code()).isNotBlank();
        assertThat(i.stage()).isIn("structural", "semantic");
        assertThat(i.message()).isNotBlank();
      });
    }

    @Test
    @DisplayName("AC-E2E-002 — correcting both allows publication")
    void ac002CorrectionPublishes() {
      // Start from a broken state, then fix both → the document is valid again.
      String broken = VALID.replace("  max_amount: 200\n", "").replace(
          "escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2\n",
          "escalation: []\n");
      assertThat(ValidatorPipeline.validate(broken).valid()).isFalse();
      // Fix: restore the limit and escalation.
      String fixed = VALID;
      assertThat(ValidatorPipeline.validate(fixed).valid()).isTrue();
    }
  }

  @Nested
  @DisplayName("financial checks are independent of shape checks")
  class Independence {
    @Test
    void escalationTargetMustBeEscalate() {
      // Point the escalation at the refund action A1 instead of A2 (an escalate action).
      String broken = replace(VALID, "target_action_id: A2", "target_action_id: A1");
      List<Issue> issues = validate(broken);
      assertThat(hasCode(issues, "FIN_ESCALATION_BOUND"))
          .as("escalation not targeting an escalate action is flagged").isTrue();
    }
  }
}
