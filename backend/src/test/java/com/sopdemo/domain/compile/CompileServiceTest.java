package com.sopdemo.domain.compile;

import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_KEY;
import static com.sopdemo.domain.issue.IssueCode.UNSUPPORTED_TYPE;
import static com.sopdemo.test.Fixtures.VALID_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.parse.ParsingService;
import com.sopdemo.domain.parse.SafeYaml;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompileServiceTest {

    private final ParsingService parse = new ParsingService(SafeYaml.ParseLimits.DEFAULT);
    private final CompileService compile = new CompileService();

    @Test
    void validTemplateCompilesToExactCanonicalContent() {
        Content c = compile.map(parse.parse(VALID_TEMPLATE)).content();
        assertThat(c).isNotNull();
        assertThat(c.sopId()).isEqualTo("BILL-REFUND-001");
        assertThat(c.title()).isEqualTo("Refund for Duplicate Charge");
        assertThat(c.ownerTeam()).isEqualTo("Billing Operations");
        assertThat(c.domain()).isEqualTo("Billing");
        assertThat(c.intent()).isEqualTo("refund_duplicate_charge");
        assertThat(c.riskLevel()).isEqualTo("medium");
        assertThat(c.maxAutonomy()).isEqualTo("assist");

        assertThat(c.policy().useWhen()).hasSize(2);
        assertThat(c.policy().doNotUseWhen()).containsExactly("Fraud is suspected.");

        assertThat(c.inputs()).hasSize(2);
        assertThat(c.inputs().get(0)).hasFieldOrPropertyWithValue("name", "refund_amount");
        assertThat(c.inputs().get(0).type()).isEqualTo("number");
        assertThat(c.inputs().get(1).type()).isEqualTo("boolean");

        assertThat(c.rules()).hasSize(2);
        assertThat(c.rules().get(0).id()).isEqualTo("R1");
        assertThat(c.rules().get(0).actionIds()).containsExactly("A1");
        assertThat(c.rules().get(0).conditions()).hasSize(2);
        assertThat(c.rules().get(0).conditions().get(0).value()).isEqualTo(Boolean.TRUE);
        assertThat(c.rules().get(0).conditions().get(1).value()).isEqualTo(200L);

        assertThat(c.actions()).hasSize(2);
        assertThat(c.actions().get(0).kind()).isEqualTo("refund");
        assertThat(c.actions().get(0).maxAmount()).isEqualTo(200.0);
        assertThat(c.actions().get(1).kind()).isEqualTo("escalate");
        assertThat(c.actions().get(1).maxAmount()).isNull();

        assertThat(c.boundaries().escalation()).hasSize(1);
        assertThat(c.boundaries().escalation().get(0).actionId()).isEqualTo("A1");
        assertThat(c.boundaries().escalation().get(0).input()).isEqualTo("refund_amount");
        assertThat(c.boundaries().escalation().get(0).op()).isEqualTo("gt");
        assertThat(c.boundaries().escalation().get(0).amount()).isEqualTo(200.0);
        assertThat(c.boundaries().escalation().get(0).targetActionId()).isEqualTo("A2");

        assertThat(c.customerMessages().primary())
                .isEqualTo("A representative can review the confirmed duplicate charge for a refund.");
        assertThat(c.customerMessages().escalation())
                .isEqualTo("This request needs additional review because it exceeds the refund limit.");
    }

    @Test
    void unknownFrontMatterKeyIsRejected() {
        String src = VALID_TEMPLATE.replace("sop_id: BILL-REFUND-001", "sop_id: BILL-REFUND-001\nbogus: 1");
        CompileService.Mapped m = compile.map(parse.parse(src));
        assertThat(m.content()).isNull();
        assertThat(m.issues()).anySatisfy(i -> assertThat(i.code()).isEqualTo(UNKNOWN_KEY.value()));
    }

    @Test
    void unknownKeyInsideAnActionIsRejected() {
        String src = VALID_TEMPLATE.replace("description: A representative may process a confirmed duplicate refund within the limit.",
                "description: A representative may process a confirmed duplicate refund within the limit.\n  bogus_field: x");
        CompileService.Mapped m = compile.map(parse.parse(src));
        assertThat(m.content()).isNull();
        assertThat(m.issues()).anySatisfy(i -> assertThat(i.code()).isEqualTo(UNKNOWN_KEY.value()));
    }

    @Test
    void wrongTypeForAFieldIsRejected() {
        // `domain: [Billing]` is a list, not a string.
        String src = VALID_TEMPLATE.replace("domain: Billing", "domain: [Billing, Support]");
        CompileService.Mapped m = compile.map(parse.parse(src));
        assertThat(m.content()).isNull();
        assertThat(m.issues()).anySatisfy(i -> assertThat(i.code()).isEqualTo(UNSUPPORTED_TYPE.value()));
    }

    @Test
    void compilationIsDeterministicForIdenticalSource() {
        List<String> a = compile.map(parse.parse(VALID_TEMPLATE)).issues().stream().map(Object::toString).toList();
        List<String> b = compile.map(parse.parse(VALID_TEMPLATE)).issues().stream().map(Object::toString).toList();
        assertThat(a).isEqualTo(b);
    }
}
