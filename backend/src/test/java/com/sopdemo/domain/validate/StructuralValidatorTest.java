package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.DOMAIN_FOR_INTENT;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_ID;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_NAME;
import static com.sopdemo.domain.issue.IssueCode.EMPTY_TEXT;
import static com.sopdemo.domain.issue.IssueCode.ENUM_INVALID;
import static com.sopdemo.domain.issue.IssueCode.MALFORMED_ID;
import static com.sopdemo.domain.issue.IssueCode.MISSING_ITEM;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_KEY;
import static com.sopdemo.domain.issue.IssueCode.UNSUPPORTED_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Rule;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StructuralValidatorTest {

    private final StructuralValidator sv = new StructuralValidator();

    private static Set<String> codes(Content c) {
        return new StructuralValidator().validate(c).stream().map(ValidationIssue::code).collect(Collectors.toSet());
    }

    private static Content setField(String sopId, String title, String owner, String domain, String intent,
                                     String risk, String autonomy, List<InputDecl> inputs, List<Rule> rules,
                                     List<com.sopdemo.domain.model.Action> actions, com.sopdemo.domain.model.Boundaries b,
                                     com.sopdemo.domain.model.CustomerMessages m) {
        return new Content(sopId, title, owner, domain, intent, risk, autonomy,
                com.sopdemo.domain.validate.ContentFactory.policy(), inputs, rules, actions, b, m);
    }

    @Test
    void validRefundHasNoStructuralIssues() {
        assertThat(sv.validate(ContentFactory.validRefund())).isEmpty();
    }

    @Test
    void invalidSopIdPatternIsReported() {
        Content c = setField("bill-refund-001", "T", "O", "Billing", "refund_duplicate_charge", "medium", "assist",
                ContentFactory.validRefund().inputs(), ContentFactory.validRefund().rules(),
                ContentFactory.validRefund().actions(), ContentFactory.validRefund().boundaries(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(MALFORMED_ID.value());
    }

    @Test
    void emptyTitleIsReported() {
        Content c = setField("BILL-REFUND-001", "", "O", "Billing", "refund_duplicate_charge", "medium", "assist",
                ContentFactory.validRefund().inputs(), ContentFactory.validRefund().rules(),
                ContentFactory.validRefund().actions(), ContentFactory.validRefund().boundaries(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(EMPTY_TEXT.value());
    }

    @Test
    void invalidDomainIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Unknown", "refund_duplicate_charge", "medium", "assist",
                ContentFactory.validRefund().inputs(), ContentFactory.validRefund().rules(),
                ContentFactory.validRefund().actions(), ContentFactory.validRefund().boundaries(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(ENUM_INVALID.value());
    }

    @Test
    void refundIntentWithSupportDomainIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Support", "refund_duplicate_charge", "medium", "assist",
                ContentFactory.validRefund().inputs(), ContentFactory.validRefund().rules(),
                ContentFactory.validRefund().actions(), ContentFactory.validRefund().boundaries(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(DOMAIN_FOR_INTENT.value());
    }

    @Test
    void emptyInputListIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Billing", "refund_duplicate_charge", "medium", "assist",
                List.of(),
                List.of(new com.sopdemo.domain.model.Rule("R1",
                        List.of(new com.sopdemo.domain.model.Condition("refund_amount", "lte", 200)),
                        List.of("A1"))),
                ContentFactory.validRefund().actions(),
                ContentFactory.validRefund().boundaries(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(MISSING_ITEM.value());
    }

    @Test
    void duplicateInputNameIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Billing", "answer_question", "low", "assist",
                List.of(new InputDecl("same", "boolean"), new InputDecl("same", "number")),
                List.of(new com.sopdemo.domain.model.Rule("R1",
                        List.of(new com.sopdemo.domain.model.Condition("same", "eq", true)),
                        List.of("A1"))),
                List.of(new com.sopdemo.domain.model.Action("A1", "human_assist", "assist", null)),
                ContentFactory.emptyEscalation(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(DUPLICATE_NAME.value());
    }

    @Test
    void duplicateRuleIdIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Billing", "answer_question", "low", "assist",
                List.of(new InputDecl("same", "boolean")),
                List.of(new com.sopdemo.domain.model.Rule("R1",
                        List.of(new com.sopdemo.domain.model.Condition("same", "eq", true)), List.of("A1")),
                        new com.sopdemo.domain.model.Rule("R1",
                                List.of(new com.sopdemo.domain.model.Condition("same", "eq", true)), List.of("A1"))),
                List.of(new com.sopdemo.domain.model.Action("A1", "human_assist", "assist", null)),
                ContentFactory.emptyEscalation(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(DUPLICATE_ID.value());
    }

    @Test
    void nonRefundActionWithMaxAmountIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Billing", "answer_question", "low", "assist",
                List.of(new InputDecl("same", "boolean")),
                List.of(new com.sopdemo.domain.model.Rule("R1",
                        List.of(new com.sopdemo.domain.model.Condition("same", "eq", true)), List.of("A1"))),
                List.of(new com.sopdemo.domain.model.Action("A1", "human_assist", "assist", 100.0)),
                ContentFactory.emptyEscalation(),
                ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(UNKNOWN_KEY.value());
    }

    @Test
    void nonPositiveEscalationAmountIsReported() {
        Content c = setField("BILL-REFUND-001", "T", "O", "Billing", "refund_duplicate_charge", "medium", "assist",
                ContentFactory.validRefund().inputs(),
                ContentFactory.validRefund().rules(),
                ContentFactory.validRefund().actions(),
                new com.sopdemo.domain.model.Boundaries(List.of(new Escalation("A1", "refund_amount", "gt", -1, "A2"))),
                com.sopdemo.domain.validate.ContentFactory.validRefund().customerMessages());
        assertThat(codes(c)).contains(UNSUPPORTED_TYPE.value());
    }
}
