package com.sopdemo.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sopdemo.content.SemanticValidator.ActionInfo;
import com.sopdemo.content.SemanticValidator.InputInfo;

/**
 * Centralized financial-safety invariants (FR-032), keyed by SOP intent:
 * <ul>
 *   <li>{@code refund_duplicate_charge}: exactly one refund action with a positive numeric max_amount;
 *       a numeric input named {@code refund_amount}; and at least one escalation boundary that names that
 *       action, uses refund_amount with op gt, amount equal to the max_amount, and targets an escalate action.</li>
 *   <li>{@code answer_question}: no refund action at all.</li>
 * </ul>
 * Missing limit and missing escalation produce distinct, readable errors (FR-032).
 */
final class FinancialSafety {

    private FinancialSafety() {
    }

    static List<Issue> check(ParsedDoc doc, SemanticValidator.Context ctx) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Object> fm = doc.frontMatter();
        if (fm == null) {
            return issues; // structural problem already reported
        }
        String intent = str(fm.get("intent"));
        if (intent == null) {
            return issues;
        }

        if (intent.equals("refund_duplicate_charge")) {
            checkRefund(doc, ctx, issues);
            if (!"Billing".equals(str(fm.get("domain")))) {
                issues.add(Issue.semantic(Codes.REFUND_INTENT_REQUIRES_BILLING,
                        "Intent refund_duplicate_charge requires domain Billing.", "frontmatter.domain"));
            }
        } else if (intent.equals("answer_question")) {
            for (ActionInfo action : ctx.actionOrder) {
                if (action.kind() != null && action.kind().equals("refund")) {
                    issues.add(Issue.semantic(Codes.REFUND_NOT_ALLOWED,
                            "Intent answer_question must not include a refund action.",
                            "actions[" + action.index() + "].kind"));
                }
            }
        }
        return issues;
    }

    private static void checkRefund(ParsedDoc doc, SemanticValidator.Context ctx, List<Issue> issues) {
        // Requirement 1: exactly one refund action with a positive finite max_amount, plus a
        // numeric input named refund_amount.
        ActionInfo refundAction = null;
        int refundCount = 0;
        for (ActionInfo action : ctx.actionOrder) {
            if (action.kind() != null && action.kind().equals("refund")) {
                refundCount++;
                refundAction = action;
            }
        }
        String refundId = refundAction != null ? refundAction.id() : null;
        Object refundMaxAmount = null;
        boolean inputOk = false;
        if (refundAction != null) {
            Object actions = doc.actions();
            if (actions instanceof List<?> list && refundAction.index() < list.size()
                    && list.get(refundAction.index()) instanceof Map<?, ?> map) {
                refundMaxAmount = map.get("max_amount");
            }
        }
        if (ctx.inputs.containsKey("refund_amount")) {
            InputInfo info = ctx.inputs.get("refund_amount");
            if (info.type() != null && info.type().equals("number")) {
                inputOk = true;
            }
        }
        boolean limitOk = refundCount == 1 && isPositiveNumber(refundMaxAmount) && inputOk;
        if (!limitOk) {
            issues.add(Issue.semantic(Codes.REFUND_LIMIT_MISSING,
                    "Refund intent requires exactly one refund action with a positive max_amount and a numeric "
                            + "input named refund_amount.",
                    "actions"));
        }

        // Requirement 2: an escalation boundary that names the refund action, uses refund_amount with
        // op gt, amount equal to the refund max_amount, and targets a declared escalate action.
        boolean escalationOk = false;
        if (refundAction != null && isPositiveNumber(refundMaxAmount) && doc.boundaries() instanceof Map<?, ?> bmap
                && bmap.get("escalation") instanceof List<?> escalation) {
            for (Object item : escalation) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                boolean ok = refundAction.id().equals(map.get("action_id"))
                        && "refund_amount".equals(map.get("input"))
                        && "gt".equals(map.get("op"))
                        && amountsEqual(map.get("amount"), refundMaxAmount)
                        && isEscalateAction(map.get("target_action_id"), ctx);
                if (ok) {
                    escalationOk = true;
                    break;
                }
            }
        }
        if (!escalationOk) {
            issues.add(Issue.semantic(Codes.REFUND_ESCALATION_MISSING,
                    "Refund intent requires an escalation boundary that names the refund action, uses the "
                            + "refund_amount input with op 'gt', an amount equal to the refund max_amount, "
                            + "and a target escalate action.",
                    "boundaries.escalation"));
        }
    }

    private static boolean isEscalateAction(Object targetId, SemanticValidator.Context ctx) {
        if (!(targetId instanceof String id) || !ctx.actions.containsKey(id)) {
            return false;
        }
        ActionInfo action = ctx.actions.get(id);
        return action.kind() != null && action.kind().equals("escalate");
    }

    private static boolean amountsEqual(Object a, Object b) {
        if (!SemanticValidator.isFiniteNumber(a) || !SemanticValidator.isFiniteNumber(b)) {
            return false;
        }
        return java.math.BigDecimal.valueOf(SemanticValidator.toDouble(a))
                .compareTo(java.math.BigDecimal.valueOf(SemanticValidator.toDouble(b))) == 0;
    }

    private static boolean isPositiveNumber(Object value) {
        if (!SemanticValidator.isFiniteNumber(value)) {
            return false;
        }
        return SemanticValidator.toDouble(value) > 0;
    }

    private static String str(Object value) {
        return SemanticValidator.str(value);
    }
}
