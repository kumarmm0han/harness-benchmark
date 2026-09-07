package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.ANSWER_QUESTION_REFUND;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_AMOUNT_MISMATCH;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_ESCALATION;
import static com.sopdemo.domain.issue.IssueCode.FINANCIAL_MISSING_LIMIT;
import static com.sopdemo.domain.issue.IssueCode.MISSING_REFUND_AMOUNT_INPUT;
import static com.sopdemo.domain.issue.IssueCode.MULTIPLE_REFUND_ACTIONS;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Action;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Escalation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Centralized financial-safety validation (FR-032, PRN-005). Declared-policy checks only —
 * the application validates and displays the policy but never executes a refund or a
 * customer decision (NFR-020).
 *
 * <ul>
 *   <li>{@code answer_question}: no {@code refund} actions allowed.</li>
 *   <li>{@code refund_duplicate_charge}:
 *     <ol>
 *       <li>limit side: exactly one {@code refund} action with a positive finite {@code max_amount}.</li>
 *       <li>input side: a numeric input named {@code refund_amount}.</li>
 *       <li>escalation side: at least one escalation boundary naming that refund action,
 *           using {@code refund_amount} with operator {@code gt}, {@code amount} equal to
 *           the refund limit, and an existing {@code escalate} action as its target.</li>
 *     </ol>
 *     A missing limit and a missing escalation produce <em>independent</em> issues
 *     (spec.md §2, AC-E2E-002).</li>
 * </ul>
 */
public final class FinancialSafety {

    private static final double EPS = 1e-9;

    public List<ValidationIssue> validate(Content c) {
        List<ValidationIssue> s = new ArrayList<>();
        String intent = c.intent() == null ? "" : c.intent().toLowerCase(Locale.ROOT);

        if (intent.equals("answer_question")) {
            if (c.actions() != null) {
                for (int i = 0; i < c.actions().size(); i++) {
                    Action a = c.actions().get(i);
                    if (a.kind() != null && a.kind().equalsIgnoreCase("refund")) {
                        add(s, ANSWER_QUESTION_REFUND,
                                "answer_question SOPs must not contain a refund action: " + a.id(),
                                "actions[" + i + "]");
                    }
                }
            }
            return s;
        }

        if (!intent.equals("refund_duplicate_charge")) {
            return s;
        }

        // ---- collect helpers ----
        List<Action> refunds = new ArrayList<>();
        Set<String> escalateIds = new HashSet<>();
        if (c.actions() != null) {
            for (Action a : c.actions()) {
                if (a.kind() == null) continue;
                String k = a.kind().toLowerCase(Locale.ROOT);
                if (k.equals("refund")) refunds.add(a);
                else if (k.equals("escalate") && a.id() != null) escalateIds.add(a.id());
            }
        }
        List<Escalation> escs = (c.boundaries() != null && c.boundaries().escalation() != null)
                ? c.boundaries().escalation() : List.of();

        boolean limitValid = false;
        Action theRefund = null;
        double limitValue = Double.NaN;
        if (refunds.size() == 1) {
            theRefund = refunds.get(0);
            Double ma = theRefund.maxAmount();
            if (ma != null && Double.isFinite(ma) && ma > 0) {
                limitValid = true;
                limitValue = ma;
            }
        }

        // ---- limit-side issue (independent) ----
        if (refunds.isEmpty()) {
            add(s, FINANCIAL_MISSING_LIMIT,
                    "requires exactly one refund action with a positive refund limit",
                    "actions");
        } else if (refunds.size() > 1) {
            add(s, MULTIPLE_REFUND_ACTIONS,
                    "requires exactly one refund action, found " + refunds.size(),
                    "actions");
        } else if (!limitValid) {
            add(s, FINANCIAL_MISSING_LIMIT,
                    "the single refund action must declare a positive finite max_amount",
                    "actions");
        }

        // ---- required input ----
        boolean hasRefundAmount = c.inputs() != null && c.inputs().stream()
                .anyMatch(x -> x.name() != null && x.name().equalsIgnoreCase("refund_amount")
                        && x.type() != null && x.type().equalsIgnoreCase("number"));
        if (!hasRefundAmount) {
            add(s, MISSING_REFUND_AMOUNT_INPUT,
                    "requires a numeric input named `refund_amount`",
                    "inputs");
        }

        // ---- escalation-side issue (independent) ----
        boolean escalateExists = !escalateIds.isEmpty();
        boolean boundaryStructurallyMatches = false;
        for (Escalation e : escs) {
            if (boundaryMatches(e, theRefund, escalateIds, false, limitValue)) {
                boundaryStructurallyMatches = true;
                break;
            }
        }
        if (!escalateExists) {
            add(s, FINANCIAL_MISSING_ESCALATION,
                    "requires an existing escalate action to target from the escalation boundary",
                    "boundaries.escalation");
        } else if (!boundaryStructurallyMatches) {
            add(s, FINANCIAL_MISSING_ESCALATION,
                    "requires an escalation boundary naming the refund action, using `refund_amount` with `gt`, targeting an escalate action",
                    "boundaries.escalation");
        } else if (limitValid) {
            boolean amountMatches = false;
            for (Escalation e : escs) {
                if (boundaryMatches(e, theRefund, escalateIds, true, limitValue)) {
                    amountMatches = true;
                    break;
                }
            }
            if (!amountMatches) {
                add(s, FINANCIAL_AMOUNT_MISMATCH,
                        "escalation amount must equal the refund action max_amount (" + fmt(limitValue) + ")",
                        "boundaries.escalation");
            }
        }

        return s;
    }

    private static boolean boundaryMatches(Escalation e, Action theRefund, Set<String> escalateIds,
                                           boolean requireAmount, double limit) {
        if (theRefund == null || e.actionId() == null || !e.actionId().equals(theRefund.id())) {
            return false;
        }
        if (e.input() == null || !e.input().equalsIgnoreCase("refund_amount")) {
            return false;
        }
        if (!"gt".equalsIgnoreCase(e.op())) {
            return false;
        }
        if (e.targetActionId() == null || !escalateIds.contains(e.targetActionId())) {
            return false;
        }
        if (requireAmount) {
            return Double.isFinite(e.amount()) && Math.abs(e.amount() - limit) <= EPS;
        }
        return true;
    }

    private static String fmt(double d) {
        return Math.abs(d - Math.rint(d)) < EPS ? Long.toString((long) d) : Double.toString(d);
    }

    private static void add(List<ValidationIssue> s, com.sopdemo.domain.issue.IssueCode code, String message, String path) {
        s.add(ValidationIssue.of(code, message, path));
    }
}
