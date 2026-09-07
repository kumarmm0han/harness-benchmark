package com.sopdemo.domain.validate;

import static com.sopdemo.domain.issue.IssueCode.DOMAIN_FOR_INTENT;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_ID;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_NAME;
import static com.sopdemo.domain.issue.IssueCode.EMPTY_TEXT;
import static com.sopdemo.domain.issue.IssueCode.ENUM_INVALID;
import static com.sopdemo.domain.issue.IssueCode.MALFORMED_ID;
import static com.sopdemo.domain.issue.IssueCode.MISSING_FIELD;
import static com.sopdemo.domain.issue.IssueCode.MISSING_ITEM;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_KEY;
import static com.sopdemo.domain.issue.IssueCode.UNSUPPORTED_TYPE;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Action;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Rule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural business checks on a well-formed {@link Content} (FR-030): required fields
 * present and nonempty, allowed enums, id/name patterns and uniqueness, and minimum counts.
 * Runs only when parsing and compilation produced no issues. Type/kind checks are already
 * done by the compile stage. Pure and independently testable (PRN-005).
 */
public final class StructuralValidator {

    static final Pattern SOP_ID = Pattern.compile("[A-Z][A-Z0-9-]{0,63}");
    static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    static final Pattern IDENT = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Set<String> DOMAINS = Set.of("Billing", "Support");
    private static final Set<String> INTENTS = Set.of("refund_duplicate_charge", "answer_question");
    private static final Set<String> RISKS = Set.of("low", "medium");
    private static final Set<String> INPUT_TYPES = Set.of("number", "boolean");
    private static final Set<String> ACTION_KINDS = Set.of("refund", "escalate", "human_assist");

    public List<ValidationIssue> validate(Content c) {
        List<ValidationIssue> s = new ArrayList<>();
        frontMatter(c, s);
        policy(c, s);
        inputs(c, s);
        rules(c, s);
        actions(c, s);
        boundaries(c, s);
        messages(c, s);
        return s;
    }

    private void frontMatter(Content c, List<ValidationIssue> s) {
        requireNonempty(c.sopId(), "frontmatter.sop_id", s);
        if (c.sopId() != null && !SOP_ID.matcher(c.sopId()).matches()) {
            add(s, MALFORMED_ID, "sop_id must match [A-Z][A-Z0-9-]{0,63}", "frontmatter.sop_id");
        }
        requireNonempty(c.title(), "frontmatter.title", s);
        requireNonempty(c.ownerTeam(), "frontmatter.owner_team", s);
        requirePresent(c.domain(), DOMAINS, "domain", "frontmatter.domain", s);
        requirePresent(c.intent(), INTENTS, "intent", "frontmatter.intent", s);
        requirePresent(c.riskLevel(), RISKS, "risk_level", "frontmatter.risk_level", s);
        if (c.maxAutonomy() == null || c.maxAutonomy().isBlank()) {
            add(s, MISSING_FIELD, "max_autonomy is required", "frontmatter.max_autonomy");
        } else if (!"assist".equals(c.maxAutonomy())) {
            add(s, ENUM_INVALID, "max_autonomy must be `assist`", "frontmatter.max_autonomy");
        }
        if ("refund_duplicate_charge".equals(c.intent()) && c.domain() != null && !"Billing".equals(c.domain())) {
            add(s, DOMAIN_FOR_INTENT, "intent refund_duplicate_charge requires domain Billing", "frontmatter.domain");
        }
    }

    private void policy(Content c, List<ValidationIssue> s) {
        if (c.policy() == null || c.policy().useWhen() == null || c.policy().useWhen().isEmpty()) {
            add(s, MISSING_ITEM, "at least one `Intent (When to use)` bullet is required", "policy.use_when");
        }
        if (c.policy() == null || c.policy().doNotUseWhen() == null || c.policy().doNotUseWhen().isEmpty()) {
            add(s, MISSING_ITEM, "at least one `Do Not Use When` bullet is required", "policy.do_not_use_when");
        }
    }

    private void inputs(Content c, List<ValidationIssue> s) {
        List<InputDecl> in = c.inputs();
        if (in == null || in.isEmpty()) {
            add(s, MISSING_ITEM, "at least one input is required", "inputs");
            return;
        }
        Set<String> seen = new HashSet<>();
        List<String> dups = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            InputDecl x = in.get(i);
            String p = "inputs[" + i + "]";
            requireNonempty(x.name(), p + ".name", s);
            if (x.name() != null && !NAME.matcher(x.name()).matches()) {
                add(s, MALFORMED_ID, "input name must match [a-z][a-z0-9_]{0,63}", p + ".name");
            }
            requirePresent(x.type(), INPUT_TYPES, "input type", p + ".type", s);
            if (x.name() != null && !x.name().isBlank() && !seen.add(x.name()) && !dups.contains(x.name())) {
                dups.add(x.name());
                add(s, DUPLICATE_NAME, "duplicate input name: " + x.name(), p + ".name");
            }
        }
    }

    private void rules(Content c, List<ValidationIssue> s) {
        List<Rule> r = c.rules();
        if (r == null || r.isEmpty()) {
            add(s, MISSING_ITEM, "at least one rule is required", "rules");
            return;
        }
        Set<String> seen = new HashSet<>();
        List<String> dups = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) {
            Rule x = r.get(i);
            String p = "rules[" + i + "]";
            requireNonempty(x.id(), p + ".id", s);
            if (x.id() != null && !IDENT.matcher(x.id()).matches()) {
                add(s, MALFORMED_ID, "rule id must match [A-Za-z][A-Za-z0-9_-]{0,63}", p + ".id");
            }
            if (x.id() != null && !x.id().isBlank() && !seen.add(x.id()) && !dups.contains(x.id())) {
                dups.add(x.id());
                add(s, DUPLICATE_ID, "duplicate rule id: " + x.id(), p + ".id");
            }
            List<Condition> conds = x.conditions();
            if (conds == null || conds.isEmpty()) {
                add(s, MISSING_ITEM, "rule must have at least one condition", p + ".conditions");
            } else {
                for (int k = 0; k < conds.size(); k++) {
                    Condition cd = conds.get(k);
                    String cp = p + ".conditions[" + k + "]";
                    requireNonempty(cd.input(), cp + ".input", s);
                    requireNonempty(cd.op(), cp + ".op", s);
                    if (cd.value() == null) {
                        add(s, MISSING_FIELD, "condition value is required", cp + ".value");
                    }
                }
            }
            if (x.actionIds() == null || x.actionIds().isEmpty()) {
                add(s, MISSING_ITEM, "rule must reference at least one action id", p + ".action_ids");
            }
        }
    }

    private void actions(Content c, List<ValidationIssue> s) {
        List<Action> a = c.actions();
        if (a == null || a.isEmpty()) {
            add(s, MISSING_ITEM, "at least one action is required", "actions");
            return;
        }
        Set<String> seen = new HashSet<>();
        List<String> dups = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            Action x = a.get(i);
            String p = "actions[" + i + "]";
            requireNonempty(x.id(), p + ".id", s);
            if (x.id() != null && !IDENT.matcher(x.id()).matches()) {
                add(s, MALFORMED_ID, "action id must match [A-Za-z][A-Za-z0-9_-]{0,63}", p + ".id");
            }
            if (x.id() != null && !x.id().isBlank() && !seen.add(x.id()) && !dups.contains(x.id())) {
                dups.add(x.id());
                add(s, DUPLICATE_ID, "duplicate action id: " + x.id(), p + ".id");
            }
            requirePresent(x.kind(), ACTION_KINDS, "action kind", p + ".kind", s);
            requireNonempty(x.description(), p + ".description", s);
            if (x.maxAmount() != null && !"refund".equals(x.kind())) {
                add(s, UNKNOWN_KEY, "max_amount is not allowed for " + x.kind() + " actions", p + ".max_amount");
            }
        }
    }

    private void boundaries(Content c, List<ValidationIssue> s) {
        if (c.boundaries() == null || c.boundaries().escalation() == null) {
            return;
        }
        List<Escalation> e = c.boundaries().escalation();
        for (int i = 0; i < e.size(); i++) {
            Escalation x = e.get(i);
            String p = "boundaries.escalation[" + i + "]";
            requireNonempty(x.actionId(), p + ".action_id", s);
            requireNonempty(x.input(), p + ".input", s);
            requireNonempty(x.op(), p + ".op", s);
            requireNonempty(x.targetActionId(), p + ".target_action_id", s);
            if (!Double.isFinite(x.amount()) || x.amount() <= 0) {
                add(s, UNSUPPORTED_TYPE, "escalation amount must be a positive finite number", p + ".amount");
            }
        }
    }

    private void messages(Content c, List<ValidationIssue> s) {
        if (c.customerMessages() == null) {
            add(s, MISSING_ITEM, "customer messages are required", "customer_messages");
            return;
        }
        requireNonempty(c.customerMessages().primary(), "customer_messages.primary", s);
        requireNonempty(c.customerMessages().escalation(), "customer_messages.escalation", s);
    }

    // ---- helper predicates ----

    private static void requireNonempty(String v, String path, List<ValidationIssue> s) {
        if (v == null) {
            add(s, MISSING_FIELD, "value is required", path);
        } else if (v.isBlank()) {
            add(s, EMPTY_TEXT, "value must not be empty", path);
        }
    }

    private static void requirePresent(String v, Set<String> allowed, String label, String path, List<ValidationIssue> s) {
        if (v == null || v.isBlank()) {
            add(s, MISSING_FIELD, label + " is required", path);
        } else if (!allowed.contains(v)) {
            add(s, ENUM_INVALID, label + " must be one of " + allowed, path);
        }
    }

    private static void add(List<ValidationIssue> s, com.sopdemo.domain.issue.IssueCode code, String message, String path) {
        s.add(ValidationIssue.of(code, message, path));
    }
}
