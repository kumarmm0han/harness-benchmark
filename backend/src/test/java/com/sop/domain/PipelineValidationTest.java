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
 * TASK-006 — structural/front-matter/type/reference validation (FR-030, PRN-005).
 *
 * <p>Each negative case mutates the valid template and asserts the expected
 * stable issue code is present.
 */
class PipelineValidationTest {

  private static String replace(String haystack, String needle, String replacement) {
    assertThat(haystack).contains(needle);
    return haystack.replace(needle, replacement);
  }

  @Nested
  @DisplayName("front matter")
  class FrontMatter {
    @Test
    void unknownFrontMatterKey() {
      String src = replace(VALID, "max_autonomy: assist", "max_autonomy: assist\nowner: someone");
      assertThat(hasCode(validate(src), "FRONTMATTER_UNKNOWN_KEY")).isTrue();
    }

    @Test
    void missingRequiredKey() {
      String src = VALID.replace("risk_level: medium\n", "");
      assertThat(hasCode(validate(src), "FIELD_MISSING")).isTrue();
    }

    @Test
    void badDomainEnum() {
      String src = replace(VALID, "domain: Billing", "domain: Marketing");
      assertThat(hasCode(validate(src), "FRONTMATTER_ENUM")).isTrue();
    }

    @Test
    void badRiskEnum() {
      String src = replace(VALID, "risk_level: medium", "risk_level: high");
      assertThat(hasCode(validate(src), "FRONTMATTER_ENUM")).isTrue();
    }

    @Test
    void badAutonomyEnum() {
      String src = replace(VALID, "max_autonomy: assist", "max_autonomy: auto");
      assertThat(hasCode(validate(src), "FRONTMATTER_ENUM")).isTrue();
    }

    @Test
    void badSopIdFormat() {
      String src = replace(VALID, "sop_id: BILL-REFUND-001", "sop_id: bill-refund-001");
      assertThat(hasCode(validate(src), "FRONTMATTER_SOP_ID_FORMAT")).isTrue();
    }

    @Test
    void emptyTitle() {
      String src = replace(VALID, "title: Refund for Duplicate Charge", "title: \"\"");
      assertThat(hasCode(validate(src), "FIELD_EMPTY")).isTrue();
    }
  }

  @Nested
  @DisplayName("unknown keys at every level")
  class UnknownKeys {
    @Test
    void unknownKeyInInput() {
      String src = replace(VALID,
          "- name: refund_amount\n  type: number",
          "- name: refund_amount\n  type: number\n  extra: 1");
      assertThat(hasCode(validate(src), "FIELD_UNKNOWN")).isTrue();
    }

    @Test
    void unknownKeyInAction() {
      String src = replace(VALID,
          "- id: A2\n  kind: escalate",
          "- id: A2\n  kind: escalate\n  meta: x");
      assertThat(hasCode(validate(src), "FIELD_UNKNOWN")).isTrue();
    }
  }

  @Nested
  @DisplayName("type and value checks")
  class Types {
    @Test
    void booleanInputWithNumericValue() {
      String src = replace(VALID,
          "op: eq\n      value: true",
          "op: eq\n      value: 5");
      assertThat(hasCode(validate(src), "REF_CONDITION_TYPE")).isTrue();
    }

    @Test
    void booleanInputUsesLteOp() {
      // duplicate_confirmed is a boolean input; `lte` is not allowed for booleans.
      // `op: eq` occurs exactly once in the valid template.
      String broken = VALID.replace("op: eq", "op: lte");
      assertThat(hasCode(validate(broken), "REF_CONDITION_OP"))
          .as("boolean input with op lte should be flagged").isTrue();
    }
  }

  @Nested
  @DisplayName("references")
  class References {
    @Test
    void conditionInputUnknown() {
      String broken = VALID.replace(
          "input: duplicate_confirmed",
          "input: not_a_real_input");
      assertThat(hasCode(validate(broken), "REF_INPUT_UNKNOWN")).isTrue();
    }

    @Test
    void ruleActionUnknown() {
      String broken = VALID.replace("action_ids: [A2]", "action_ids: [A99]");
      assertThat(hasCode(validate(broken), "REF_ACTION_UNKNOWN")).isTrue();
    }

    @Test
    void duplicateInputName() {
      String broken = VALID.replace(
          "- name: duplicate_confirmed", "- name: refund_amount");
      assertThat(hasCode(validate(broken), "DUPLICATE_INPUT_NAME")).isTrue();
    }

    @Test
    void duplicateActionId() {
      String broken = VALID.replace("- id: A2\n  kind: escalate", "- id: A1\n  kind: escalate");
      assertThat(hasCode(validate(broken), "DUPLICATE_ACTION_ID")).isTrue();
    }
  }

  @Nested
  @DisplayName("empty collections")
  class Empty {
    @Test
    void noInputs() {
      // Replace the two input entries with nothing to leave an empty yaml block.
      String broken = VALID.replace("```yaml\n- name: refund_amount\n  type: number\n- name: duplicate_confirmed\n  type: boolean\n```",
          "```yaml\n```");
      assertThat(hasCode(validate(broken), "SECTION_SHAPE")).isTrue(); // empty block
    }
  }

  @Nested
  @DisplayName("answer_question invariants")
  class AnswerQuestion {
    @Test
    void refundActionNotAllowed() {
      String broken = VALID
          .replace("intent: refund_duplicate_charge", "intent: answer_question");
      // A refund action remains in the broken doc → must be flagged.
      assertThat(hasCode(validate(broken), "QUESTION_REFUND_NOT_ALLOWED")).isTrue();
    }
  }

  @Test
  @DisplayName("the valid template yields zero issues and real content")
  void validTemplateIsClean() {
    PipelineResult r = ValidatorPipeline.validate(VALID);
    List<Issue> issues = r.issues();
    assertThat(issues).isEmpty();
    Content c = ValidatorPipeline.buildContent(r.parsedDocument());
    assertThat(c.sopId()).isEqualTo("BILL-REFUND-001");
    assertThat(c.domain()).isEqualTo("Billing");
    assertThat(c.intent()).isEqualTo("refund_duplicate_charge");
    assertThat(c.maxAutonomy()).isEqualTo("assist");
    assertThat(c.inputs()).hasSize(2);
    assertThat(c.rules()).hasSize(2);
    assertThat(c.actions()).hasSize(2);
    assertThat(c.policy().useWhen()).hasSize(2);
  }
}
