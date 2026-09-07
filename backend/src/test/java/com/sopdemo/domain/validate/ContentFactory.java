package com.sopdemo.domain.validate;

import com.sopdemo.domain.model.Action;
import com.sopdemo.domain.model.Boundaries;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.CustomerMessages;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Policy;
import com.sopdemo.domain.model.Rule;
import java.util.List;

/** Builds canonical {@link Content} objects directly so validator tiers can be tested in isolation. */
public final class ContentFactory {
    private ContentFactory() {}

    public static Policy policy() {
        return new Policy(List.of("Customer reports a duplicate charge.", "Confirmed duplicate."),
                List.of("Fraud is suspected."));
    }

    public static InputDecl refundInput() {
        return new InputDecl("refund_amount", "number");
    }

    public static Action refund(String id, Double maxAmount) {
        return new Action(id, "refund", "process a refund", maxAmount);
    }

    public static Action escalate(String id) {
        return new Action(id, "escalate", "refer to support", null);
    }

    public static Boundaries oneEscalation(String actionId, double amount, String target) {
        return new Boundaries(List.of(new Escalation(actionId, "refund_amount", "gt", amount, target)));
    }

    public static Boundaries emptyEscalation() {
        return new Boundaries(List.of());
    }

    public static Content validRefund() {
        return new Content(
                "BILL-REFUND-001", "Refund for Duplicate Charge", "Billing Operations",
                "Billing", "refund_duplicate_charge", "medium", "assist",
                policy(),
                List.of(refundInput(), new InputDecl("duplicate_confirmed", "boolean")),
                List.of(new Rule("R1", List.of(new Condition("refund_amount", "lte", 200)), List.of("A1"))),
                List.of(refund("A1", 200.0), escalate("A2")),
                oneEscalation("A1", 200.0, "A2"),
                new CustomerMessages(
                        "A representative can review the confirmed duplicate charge for a refund.",
                        "This request needs additional review because it exceeds the refund limit."));
    }
}
