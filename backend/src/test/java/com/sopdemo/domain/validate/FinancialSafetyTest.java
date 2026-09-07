package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.ANSWER_QUESTION_REFUND;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_AMOUNT_MISMATCH;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_ESCALATION;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_LIMIT;
import static com.sopdemo.domain.issue.IssueCode.MISSING_REFUND_AMOUNT_INPUT;
import static com.sopdemo.domain.issue.IssueCode.MULTIPLE_REFUND_ACTIONS;
import static com.sopdemo.domain.validate.ContentFactory.escalate;
import static com.sopdemo.domain.validate.ContentFactory.policy;
import static com.sopdemo.domain.validate.ContentFactory.refund;
import static com.sopdemo.domain.validate.ContentFactory.refundInput;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Action;
import com.sopdemo.domain.model.Boundaries;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.CustomerMessages;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Rule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancialSafetyTest {

    private final FinancialSafety fs = new FinancialSafety();

    private static Set<String> codes(Content c) {
        return new FinancialSafety().validate(c).stream().map(ValidationIssue::code).collect(java.util.stream.Collectors.toSet());
    }

    private static Content with(Content base, List<Action> actions, Boundaries boundaries, List<InputDecl> inputs) {
        return new Content(base.sopId(), base.title(), base.ownerTeam(), base.domain(), base.intent(),
                base.riskLevel(), base.maxAutonomy(), policy(),
                inputs, base.rules(), actions, boundaries, base.customerMessages());
    }

    @Test
    void validRefundHasNoFinancialIssues() {
        assertThat(fs.validate(ContentFactory.validRefund())).isEmpty();
    }

    @Test
    void missingLimitIsReported() {
        Content c = with(ContentFactory.validRefund(),
                List.of(refund("A1", null), escalate("A2")), ContentFactory.oneEscalation("A1", 200.0, "A2"),
                List.of(refundInput()));
        assertThat(codes(c)).contains(FINANCIAL_MISSING_LIMIT.value());
    }

    @Test
    void missingEscalationBoundaryIsReported() {
        Content c = with(ContentFactory.validRefund(),
                List.of(refund("A1", 200.0), escalate("A2")), ContentFactory.emptyEscalation(),
                List.of(refundInput()));
        assertThat(codes(c)).contains(FINANCIAL_MISSING_ESCALATION.value());
    }

    @Test
    void acE2E002_missingLimitAndMissingEscalationAreSeparateIssues() {
        // Both the refund limit (max_amount) and the escalation boundary are removed.
        Content c = with(ContentFactory.validRefund(),
                List.of(refund("A1", null), escalate("A2")), ContentFactory.emptyEscalation(),
                List.of(refundInput()));
        Set<String> got = codes(c);
        assertThat(got).contains(FINANCIAL_MISSING_LIMIT.value(), FINANCIAL_MISSING_ESCALATION.value());
    }

    @Test
    void multipleRefundActionsIsReported() {
        Content c = with(ContentFactory.validRefund(),
                List.of(refund("A1", 200.0), refund("A3", 100.0), escalate("A2")),
                ContentFactory.oneEscalation("A1", 200.0, "A2"), List.of(refundInput()));
        assertThat(codes(c)).contains(MULTIPLE_REFUND_ACTIONS.value());
    }

    @Test
    void missingRefundAmountInputIsReported() {
        Content c = with(ContentFactory.validRefund(),
                ContentFactory.validRefund().actions(),
                ContentFactory.oneEscalation("A1", 200.0, "A2"),
                List.of(new InputDecl("other_amount", "number")));
        assertThat(codes(c)).contains(MISSING_REFUND_AMOUNT_INPUT.value());
    }

    @Test
    void escalationAmountMismatchIsReported() {
        Content c = with(ContentFactory.validRefund(),
                List.of(refund("A1", 200.0), escalate("A2")), ContentFactory.oneEscalation("A1", 300.0, "A2"),
                List.of(refundInput()));
        assertThat(codes(c)).contains(FINANCIAL_AMOUNT_MISMATCH.value());
    }

    @Test
    void answerQuestionMustNotHaveRefundActions() {
        Content base = ContentFactory.validRefund();
        Content c = new Content(base.sopId(), base.title(), base.ownerTeam(), "Support",
                "answer_question", "low", "assist", policy(),
                List.of(new InputDecl("flag", "boolean")),
                List.of(new Rule("R1", List.of(new Condition("flag", "eq", true)), List.of("A1"))),
                List.of(new Action("A1", "human_assist", "assist a human", null)),
                ContentFactory.emptyEscalation(), base.customerMessages());
        // human_assist only is fine:
        assertThat(fs.validate(c)).isEmpty();

        Content bad = new Content(base.sopId(), base.title(), base.ownerTeam(), "Support",
                "answer_question", "low", "assist", policy(),
                List.of(new InputDecl("flag", "boolean")),
                List.of(new Rule("R1", List.of(new Condition("flag", "eq", true)), List.of("A1"))),
                List.of(refund("A1", 100.0)),
                ContentFactory.emptyEscalation(), base.customerMessages());
        assertThat(codes(bad)).contains(ANSWER_QUESTION_REFUND.value());
    }
}
