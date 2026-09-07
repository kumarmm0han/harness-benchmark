package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_ESCALATION;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_LIMIT;
import static com.sopdemo.domain.issue.IssueCode.MISSING_SECTION;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_INPUT_REFERENCE;
import static com.sopdemo.test.Fixtures.VALID_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.PolicyResult;
import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.parse.SafeYaml;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ValidationServiceTest {

    private final ValidationService svc = new ValidationService(SafeYaml.ParseLimits.DEFAULT);

    @Test
    void validTemplateIsValidWithContent() {
        PolicyResult r = svc.validate(VALID_TEMPLATE);
        assertThat(r.valid()).isTrue();
        assertThat(r.issues()).isEmpty();
        assertThat(r.content()).isNotNull();
        assertThat(r.content().sopId()).isEqualTo("BILL-REFUND-001");
        assertThat(r.content().domain()).isEqualTo("Billing");
    }

    @Test
    void invalidContentYieldsNullContent() {
        String bad = VALID_TEMPLATE.replace("## Do Not Use When\n- Fraud is suspected.\n\n", "");
        PolicyResult r = svc.validate(bad);
        assertThat(r.valid()).isFalse();
        assertThat(r.content()).isNull();
    }

    @Test
    void removingEscalationBoundaryFailsFinancialSafety() {
        String bad = VALID_TEMPLATE.replace(
                "escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2\n",
                "escalation: []\n");
        PolicyResult r = svc.validate(bad);
        assertThat(r.valid()).isFalse();
        assertThat(r.issues().stream().map(ValidationIssue::code)).contains(FINANCIAL_MISSING_ESCALATION.value());
    }

    @Test
    void removingRefundLimitFailsFinancialSafety() {
        String bad = VALID_TEMPLATE.replace("  max_amount: 200\n", "");
        PolicyResult r = svc.validate(bad);
        assertThat(r.valid()).isFalse();
        assertThat(r.issues().stream().map(ValidationIssue::code)).contains(FINANCIAL_MISSING_LIMIT.value());
    }

    @Test
    void semanticChecksAreOmittedAfterAStructuralFailure() {
        // Unknown section (structural) + would-be unknown input (semantic). Only structural surfaces.
        String bad = VALID_TEMPLATE
                .replace("## Do Not Use When\n- Fraud is suspected.\n\n", "")
                .replace("input: refund_amount\n", "input: not_defined\n");
        PolicyResult r = svc.validate(bad);
        assertThat(r.valid()).isFalse();
        List<String> got = r.issues().stream().map(ValidationIssue::code).collect(Collectors.toList());
        assertThat(got).contains(MISSING_SECTION.value());
        assertThat(got).doesNotContain(UNKNOWN_INPUT_REFERENCE.value());
    }

    @Test
    void issuesAreDeterministicAndStablyOrdered() {
        String bad = VALID_TEMPLATE.replace("## Do Not Use When\n- Fraud is suspected.\n\n", "")
                + "\n## Bogus\n- x\n";
        List<ValidationIssue> a = svc.validate(bad).issues();
        List<ValidationIssue> b = svc.validate(bad).issues();
        assertThat(a).isEqualTo(b).isSortedAccordingTo(ValidationIssue.STABLE_ORDER);
        assertThat(a).isNotEmpty();
    }
}
