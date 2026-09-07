package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.BOOL_OP_RESTRICTION;
import static com.sopdemo.domain.issue.IssueCode.INVALID_OP;
import static com.sopdemo.domain.issue.IssueCode.OP_VALUE_TYPE_MISMATCH;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_ACTION_REFERENCE;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_INPUT_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.Rule;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReferenceValidatorTest {

    private final ReferenceValidator rv = new ReferenceValidator();

    private static Set<String> codes(List<ValidationIssue> i) {
        return i.stream().map(ValidationIssue::code).collect(Collectors.toSet());
    }

    private static Content withRules(Rule... rules) {
        Content base = ContentFactory.validRefund();
        return new Content(base.sopId(), base.title(), base.ownerTeam(), base.domain(), base.intent(),
                base.riskLevel(), base.maxAutonomy(), base.policy(), base.inputs(), List.of(rules),
                base.actions(), base.boundaries(), base.customerMessages());
    }

    @Test
    void validReferencesHaveNoIssues() {
        assertThat(rv.validate(ContentFactory.validRefund())).isEmpty();
    }

    @Test
    void unknownConditionInputIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("does_not_exist", "eq", true)), List.of("A1")));
        assertThat(codes(rv.validate(c))).contains(UNKNOWN_INPUT_REFERENCE.value());
    }

    @Test
    void unknownActionReferenceInRuleIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("refund_amount", "lte", 200)), List.of("ZZZ")));
        assertThat(codes(rv.validate(c))).contains(UNKNOWN_ACTION_REFERENCE.value());
    }

    @Test
    void unknownEscalationActionReferenceIsReported() {
        Content base = ContentFactory.validRefund();
        Content c = new Content(base.sopId(), base.title(), base.ownerTeam(), base.domain(), base.intent(),
                base.riskLevel(), base.maxAutonomy(), base.policy(), base.inputs(), base.rules(),
                base.actions().subList(0, 1), // only the refund action, so the escalate target does not exist
                new com.sopdemo.domain.model.Boundaries(List.of(new Escalation("A1", "refund_amount", "gt", 200.0, "A999"))),
                base.customerMessages());
        assertThat(codes(rv.validate(c))).contains(UNKNOWN_ACTION_REFERENCE.value());
    }

    @Test
    void booleanInputWithGtIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("duplicate_confirmed", "gt", true)), List.of("A1")));
        assertThat(codes(rv.validate(c))).contains(BOOL_OP_RESTRICTION.value());
    }

    @Test
    void booleanInputWithNonBooleanValueIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("duplicate_confirmed", "eq", 5)), List.of("A1")));
        assertThat(codes(rv.validate(c))).contains(OP_VALUE_TYPE_MISMATCH.value());
    }

    @Test
    void numericInputWithBooleanValueIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("refund_amount", "lte", true)), List.of("A1")));
        assertThat(codes(rv.validate(c))).contains(OP_VALUE_TYPE_MISMATCH.value());
    }

    @Test
    void invalidOperatorIsReported() {
        Content c = withRules(new Rule("R1", List.of(new Condition("refund_amount", "neq", 200)), List.of("A1")));
        assertThat(codes(rv.validate(c))).contains(INVALID_OP.value());
    }
}
