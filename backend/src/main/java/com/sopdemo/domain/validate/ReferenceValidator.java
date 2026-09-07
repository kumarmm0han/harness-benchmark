package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.BOOL_OP_RESTRICTION;
import static com.sopdemo.domain.issue.IssueCode.INVALID_OP;
import static com.sopdemo.domain.issue.IssueCode.OP_VALUE_TYPE_MISMATCH;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_ACTION_REFERENCE;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_INPUT_REFERENCE;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Rule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-reference checks (semantic, FR-030): every condition input and referenced action id
 * must exist; operators must be valid and match the declared input type (numbers for numeric,
 * booleans for boolean, with {@code eq} only for boolean inputs). Runs only when structural
 * checks found no blocking problem (FR-034). Pure and independently testable (PRN-005).
 */
public final class ReferenceValidator {

    private static final Set<String> OPS = Set.of("eq", "gt", "lte");

    public List<ValidationIssue> validate(Content c) {
        List<ValidationIssue> s = new ArrayList<>();
        Map<String, InputDecl> inputs = new HashMap<>();
        if (c.inputs() != null) {
            for (InputDecl i : c.inputs()) {
                if (i.name() != null) {
                    inputs.put(i.name(), i);
                }
            }
        }
        Set<String> actionIds = new HashSet<>();
        if (c.actions() != null) {
            for (var a : c.actions()) {
                if (a.id() != null) {
                    actionIds.add(a.id());
                }
            }
        }
        if (c.rules() != null) {
            for (int i = 0; i < c.rules().size(); i++) {
                Rule r = c.rules().get(i);
                String base = "rules[" + i + "]";
                if (r.conditions() != null) {
                    for (int k = 0; k < r.conditions().size(); k++) {
                        Condition cd = r.conditions().get(k);
                        String p = base + ".conditions[" + k + "]";
                        InputDecl in = inputs.get(cd.input());
                        if (in == null) {
                            add(s, UNKNOWN_INPUT_REFERENCE, "condition references unknown input: " + cd.input(), p + ".input");
                        } else {
                            checkOp(cd, in, p, s);
                        }
                    }
                }
                if (r.actionIds() != null) {
                    for (int k = 0; k < r.actionIds().size(); k++) {
                        String aid = r.actionIds().get(k);
                        if (!actionIds.contains(aid)) {
                            add(s, UNKNOWN_ACTION_REFERENCE, "rule references unknown action: " + aid, base + ".action_ids[" + k + "]");
                        }
                    }
                }
            }
        }
        if (c.boundaries() != null && c.boundaries().escalation() != null) {
            List<Escalation> e = c.boundaries().escalation();
            for (int i = 0; i < e.size(); i++) {
                Escalation x = e.get(i);
                String p = "boundaries.escalation[" + i + "]";
                if (x.actionId() != null && !actionIds.contains(x.actionId())) {
                    add(s, UNKNOWN_ACTION_REFERENCE, "escalation references unknown action: " + x.actionId(), p + ".action_id");
                }
                if (x.targetActionId() != null && !actionIds.contains(x.targetActionId())) {
                    add(s, UNKNOWN_ACTION_REFERENCE, "escalation references unknown target action: " + x.targetActionId(), p + ".target_action_id");
                }
            }
        }
        return s;
    }

    private void checkOp(Condition cd, InputDecl in, String p, List<ValidationIssue> s) {
        String op = cd.op();
        boolean isBool = "boolean".equalsIgnoreCase(in.type());
        boolean isNum = "number".equalsIgnoreCase(in.type());
        if (!OPS.contains(op)) {
            add(s, INVALID_OP, "operator must be one of " + OPS, p + ".op");
        }
        if (isBool && !"eq".equals(op)) {
            add(s, BOOL_OP_RESTRICTION, "boolean inputs only support the `eq` operator", p + ".op");
        }
        Object v = cd.value();
        if (v != null) {
            if (isBool && !(v instanceof Boolean)) {
                add(s, OP_VALUE_TYPE_MISMATCH, "boolean input requires a boolean value", p + ".value");
            } else if (isNum && !(v instanceof Number)) {
                add(s, OP_VALUE_TYPE_MISMATCH, "numeric input requires a numeric value", p + ".value");
            }
        }
    }

    private static void add(List<ValidationIssue> s, com.sopdemo.domain.issue.IssueCode code, String message, String path) {
        s.add(ValidationIssue.of(code, message, path));
    }
}
