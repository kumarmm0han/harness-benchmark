package com.sopdemo.domain.parse;

import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_SECTION;
import static com.sopdemo.domain.issue.IssueCode.FENCE_STRUCTURE_INVALID;
import static com.sopdemo.domain.issue.IssueCode.MISSING_SECTION;
import static com.sopdemo.domain.issue.IssueCode.PROSE_STRUCTURE_INVALID;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_SECTION;
import static com.sopdemo.test.Fixtures.VALID_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.parse.SafeYaml.ParseLimits;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ParsingServiceTest {

    private final ParsingService svc = new ParsingService(ParseLimits.DEFAULT);

    private Set<String> codes(String src) {
        return svc.parse(src).issues().stream().map(ValidationIssue::code).collect(Collectors.toSet());
    }

    @Test
    void validTemplateHasNoIssuesAndAllSectionsPresent() {
        ParsedSource ps = svc.parse(VALID_TEMPLATE);
        assertThat(ps.issues()).isEmpty();
        assertThat(SectionKind.BY_TITLE.values())
                .allSatisfy(k -> assertThat(ps.section(k)).isNotNull());
        Map<Object, Object> fm = ps.frontMatterMap();
        assertThat(fm.get("sop_id")).isEqualTo("BILL-REFUND-001");
        assertThat(fm.get("domain")).isEqualTo("Billing");
        assertThat(fm.get("risk_level")).isEqualTo("medium");
        assertThat(ps.section(SectionKind.INPUTS).value()).isInstanceOf(List.class);
        assertThat(ps.section(SectionKind.BOUNDARIES).value()).isInstanceOf(Map.class);
        assertThat(ps.section(SectionKind.USE_WHEN).bullets())
                .containsExactly(
                        "Customer reports a duplicate charge.",
                        "A support representative confirms the duplicate.");
    }

    @Test
    void unknownSectionIsRejected() {
        Set<String> issues = codes(VALID_TEMPLATE + "\n## Bogus Section\n- something\n");
        assertThat(issues).contains(UNKNOWN_SECTION.value());
    }

    @Test
    void missingRequiredSectionIsRejected() {
        String src = VALID_TEMPLATE.replace("## Do Not Use When\n- Fraud is suspected.\n\n", "");
        assertThat(codes(src)).contains(MISSING_SECTION.value());
    }

    @Test
    void duplicatedSectionIsRejected() {
        String src = VALID_TEMPLATE.replace("## Customer Messages", "## Customer Messages\n\n## Customer Messages");
        assertThat(svc.parse(src).issues().stream().map(ValidationIssue::code))
                .contains(DUPLICATE_SECTION.value());
    }

    @Test
    void nonBulletLineInProseSectionIsRejected() {
        String src = VALID_TEMPLATE.replace("- Customer reports a duplicate charge.",
                "Customer reports a duplicate charge plainly, without a bullet marker.");
        assertThat(codes(src)).contains(PROSE_STRUCTURE_INVALID.value());
    }

    @Test
    void proseSectionWithInlineHtmlIsRetainedAsTextNotInterpreted() {
        String src = VALID_TEMPLATE.replace("- Fraud is suspected.", "- Fraud is suspected. <b>risk</b> &amp; more");
        ParsedSource ps = svc.parse(src);
        // The bullet (with raw HTML text) is retained verbatim as a string - it is data, not markup.
        assertThat(ps.issues()).isEmpty();
        assertThat(ps.section(SectionKind.DO_NOT_USE_WHEN).bullets())
                .containsExactly("Fraud is suspected. <b>risk</b> &amp; more");
    }

    @Test
    void machineSectionWithNoYamlFenceIsRejected() {
        // Replace the whole fenced inputs block with plain (non-fenced) prose.
        String src = VALID_TEMPLATE.replace(
                "```yaml\n- name: refund_amount\n  type: number\n- name: duplicate_confirmed\n  type: boolean\n```",
                "hello world, not a yaml fenced block");
        assertThat(codes(src)).contains(FENCE_STRUCTURE_INVALID.value());
    }

    @Test
    void machineSectionFenceWithoutYamlLanguageIsRejected() {
        // A fence that is not labeled `yaml` must be rejected (FR-020: machine sections are `yaml`).
        String src = VALID_TEMPLATE.replace("```yaml\n- name: refund_amount", "```json\n- name: refund_amount");
        assertThat(codes(src)).contains(FENCE_STRUCTURE_INVALID.value());
    }

    @Test
    void deterministicIssuesAreStablyOrderedForIdenticalSource() {
        String src = VALID_TEMPLATE
                .replace("## Do Not Use When\n- Fraud is suspected.\n\n", "")
                + "\n## Bogus Section\n- something\n";
        List<ValidationIssue> a = svc.parse(src).issues();
        List<ValidationIssue> b = svc.parse(src).issues();
        assertThat(a).isEqualTo(b);
        assertThat(a).isSortedAccordingTo(ValidationIssue.STABLE_ORDER);
    }
}
