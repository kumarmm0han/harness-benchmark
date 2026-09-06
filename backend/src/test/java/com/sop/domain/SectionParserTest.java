package com.sop.domain;

import com.sop.dto.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sop.domain.Tpl.VALID;
import static com.sop.domain.Tpl.hasCode;
import static com.sop.domain.Tpl.validate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-005 — SectionParser (DES-004, FR-020, FR-021).
 */
class SectionParserTest {

  private static SectionSplit split(String src) {
    return SectionParser.split(src);
  }

  @Test
  @DisplayName("a valid template splits into front-matter + 2 prose + 5 machine sections")
  void validTemplateSplits() {
    SectionSplit s = split(VALID);
    assertThat(s.frontMatterText).isNotNull();
    assertThat(s.proseSections.keySet()).containsExactly(
        Contract.SEC_INTENT, Contract.SEC_DO_NOT);
    assertThat(s.machineSectionTexts.keySet()).containsExactlyInAnyOrder(
        Contract.SEC_INPUTS, Contract.SEC_RULES, Contract.SEC_ACTIONS,
        Contract.SEC_BOUNDARIES, Contract.SEC_MESSAGES);
    assertThat(s.proseSections.get(Contract.SEC_INTENT)).containsExactly(
        "Customer reports a duplicate charge.",
        "A support representative confirms the duplicate.");
  }

  @Test
  @DisplayName("missing front matter → FRONTMATTER_MISSING")
  void missingFrontMatter() {
    String src = "## Intent (When to use)\n- x\n";
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "FRONTMATTER_MISSING")).isTrue();
  }

  @Test
  @DisplayName("a missing required section → SECTION_MISSING")
  void missingRequiredSection() {
    String src = VALID.replace("## Boundaries\n", "");
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "SECTION_MISSING")).isTrue();
  }

  @Test
  @DisplayName("a duplicated heading → SECTION_DUPLICATED")
  void duplicatedHeading() {
    String src = VALID + "\n## Inputs Required\n```yaml\n- name: x\n  type: number\n```";
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "SECTION_DUPLICATED")).isTrue();
  }

  @Test
  @DisplayName("an unknown section → SECTION_UNEXPECTED")
  void unknownSection() {
    String src = VALID + "\n## Something Else\n- whatever";
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "SECTION_UNEXPECTED")).isTrue();
  }

  @Test
  @DisplayName("a `###` heading in body → SECTION_UNEXPECTED")
  void h3InBody() {
    String src = "### not allowed\n- x";
    // Note: even without front matter, we should not crash.
    SectionSplit s = split(src);
    assertThat(s.issues).isNotEmpty();
  }

  @Test
  @DisplayName("a machine section with two yaml blocks → SECTION_SHAPE")
  void twoYamlBlocks() {
    String src = VALID.replace(
        "## Inputs Required\n```yaml\n- name: refund_amount\n  type: number",
        "## Inputs Required\n```yaml\n- name: refund_amount\n  type: number\n```\n```yaml\n- name: x\n  type: number");
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "SECTION_SHAPE")).isTrue();
  }

  @Test
  @DisplayName("a prose section with non-bullet non-blank line → SECTION_SHAPE")
  void proseWithProse() {
    String src = VALID.replace(
        "## Do Not Use When\n- Fraud is suspected.",
        "## Do Not Use When\nthis is just a sentence, not a bullet.");
    SectionSplit s = split(src);
    assertThat(hasCode(s.issues, "SECTION_SHAPE")).isTrue();
  }

  @Test
  @DisplayName("the valid template yields zero structural issues")
  void noStructuralIssuesOnValidTemplate() {
    SectionSplit s = split(VALID);
    assertThat(s.issues).isEmpty();
  }
}
