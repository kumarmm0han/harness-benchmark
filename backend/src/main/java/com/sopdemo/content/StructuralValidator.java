package com.sopdemo.content;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validation (spec.md section 2, FR-030, FR-034 structural stage).
 * Checks per-structure required fields, allowed fields, enums and value types.
 * Centralized so codes/paths/messages stay stable (PRN-005).
 */
final class StructuralValidator {

    private static final Pattern SOP_ID = Pattern.compile("[A-Z][A-Z0-9-]{0,63}");
    private static final Pattern INPUT_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern RULE_ACTION_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private static final List<String> FM_FIELDS = List.of(
            "sop_id", "title", "owner_team", "domain", "intent", "risk_level", "max_autonomy");
    private static final Set<String> DOMAINS = Set.of("Billing", "Support");
    private static final Set<String> INTENTS = Set.of("refund_duplicate_charge", "answer_question");
    private static final Set<String> RISKS = Set.of("low", "medium");
    private static final Set<String> AUTONOMIES = Set.of("assist");

    private static final Set<String> INPUT_FIELDS = Set.of("name", "type");
    private static final Set<String> INPUT_TYPES = Set.of("number", "boolean");
    private static final Set<String> RULE_FIELDS = Set.of("id", "conditions", "action_ids");
    private static final Set<String> CONDITION_FIELDS = Set.of("input", "op", "value");
    private static final Set<String> OPS = Set.of("eq", "gt", "lte");
    private static final Set<String> ACTION_FIELDS = Set.of("id", "kind", "description", "max_amount");
    private static final Set<String> ACTION_KINDS = Set.of("refund", "escalate", "human_assist");
    private static final Set<String> BOUNDARY_FIELDS = Set.of("escalation");
    private static final Set<String> ESCALATION_FIELDS = Set.of("action_id", "input", "op", "amount", "target_action_id");
    private static final Set<String> MESSAGE_FIELDS = Set.of("primary", "escalation");

    private StructuralValidator() {
    }

    static List<Issue> validate(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>(doc.issues());
        List<String> useWhen = doc.useWhen();
        issues.addAll(checkFrontMatter(doc, useWhen));
        issues.addAll(checkBullets("use_when", doc.useWhen(), "section:Intent (When to use)"));
        issues.addAll(checkBullets("do_not_use_when", doc.doNotUseWhen(), "section:Do Not Use When"));
        issues.addAll(checkInputs(doc));
        issues.addAll(checkRules(doc));
        issues.addAll(checkActions(doc));
        issues.addAll(checkBoundaries(doc));
        issues.addAll(checkMessages(doc));
        return issues;
    }

    private static List<Issue> checkFrontMatter(ParsedDoc doc, List<String> useWhen) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Object> fm = doc.frontMatter();
        if (fm == null) {
            return issues; // front matter problems already reported by the parser
        }
        for (String field : FM_FIELDS) {
            if (!fm.containsKey(field)) {
                issues.add(Issue.structural(Codes.MISSING_FIELD,
                        "Front matter field '" + field + "' is required.", "frontmatter." + field));
            }
        }
        for (String key : fm.keySet()) {
            if (!FM_FIELDS.contains(key)) {
                issues.add(Issue.structural(Codes.UNKNOWN_FIELD,
                        "Front matter field '" + key + "' is not supported.", "frontmatter." + key));
            }
        }
        for (String field : FM_FIELDS) {
            Object value = fm.get(field);
            if (value == null) {
                continue;
            }
            if (!(value instanceof String)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE,
                        "Front matter field '" + field + "' must be a string.", "frontmatter." + field));
                continue;
            }
            String s = (String) value;
            switch (field) {
                case "sop_id" -> {
                    if (!SOP_ID.matcher(s).matches()) {
                        issues.add(Issue.structural(Codes.INVALID_ID,
                                "sop_id must match [A-Z][A-Z0-9-]{0,63} (uppercase letter, then letters/digits/dashes, max 64 chars).",
                                "frontmatter.sop_id"));
                    }
                }
                case "title" -> {
                    if (s.isEmpty()) {
                        issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                                "title must be nonempty.", "frontmatter.title"));
                    }
                }
                case "owner_team" -> {
                    if (s.isEmpty()) {
                        issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                                "owner_team must be nonempty.", "frontmatter.owner_team"));
                    }
                }
                case "domain" -> {
                    if (!DOMAINS.contains(s)) {
                        issues.add(Issue.structural(Codes.INVALID_ENUM,
                                "domain must be one of: Billing, Support.", "frontmatter.domain"));
                    }
                }
                case "intent" -> {
                    if (!INTENTS.contains(s)) {
                        issues.add(Issue.structural(Codes.INVALID_ENUM,
                                "intent must be one of: refund_duplicate_charge, answer_question.", "frontmatter.intent"));
                    }
                }
                case "risk_level" -> {
                    if (!RISKS.contains(s)) {
                        issues.add(Issue.structural(Codes.INVALID_ENUM,
                                "risk_level must be one of: low, medium.", "frontmatter.risk_level"));
                    }
                }
                case "max_autonomy" -> {
                    if (!AUTONOMIES.contains(s)) {
                        issues.add(Issue.structural(Codes.INVALID_ENUM,
                                "max_autonomy must be 'assist'.", "frontmatter.max_autonomy"));
                    }
                }
                default -> {
                    // no extra constraint
                }
            }
        }
        return issues;
    }

    private static List<Issue> checkBullets(String label, List<String> items, String path) {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String text = items.get(i);
            if (text == null || text.isEmpty()) {
                issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                        "Bullet " + (i + 1) + " in '" + label + "' must contain text.", path + "[" + i + "]"));
            }
        }
        return issues;
    }

    private static List<Issue> checkInputs(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        List<Object> inputs = YamlSafe.asList(doc.inputs());
        if (doc.inputs() != null && inputs == null) {
            issues.add(Issue.structural(Codes.WRONG_SECTION_TYPE,
                    "'Inputs Required' must be a YAML list of input objects.", "section:Inputs Required"));
            return issues;
        }
        if (inputs == null) {
            return issues; // section problems already reported
        }
        for (int i = 0; i < inputs.size(); i++) {
            String path = "inputs[" + i + "]";
            Object item = inputs.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE, "Inputs Required entry " + (i + 1) + " must be a mapping.", path));
                continue;
            }
            String name = stringValue(map.get("name"));
            boolean hasName = checkRequired(map, "name", path, issues);
            checkFieldNames(map, INPUT_FIELDS, path, issues);
            if (hasName && name != null && !INPUT_NAME.matcher(name).matches()) {
                issues.add(Issue.structural(Codes.INVALID_ID,
                        "Input name must match [a-z][a-z0-9_]{0,63} (lowercase letters/digits/underscore).", path + ".name"));
            }
            boolean hasType = checkRequired(map, "type", path, issues);
            if (hasType) {
                Object type = map.get("type");
                if (!(type instanceof String ts) || !INPUT_TYPES.contains(ts)) {
                    issues.add(Issue.structural(Codes.INVALID_ENUM,
                            "Input type must be one of: number, boolean.", path + ".type"));
                }
            }
        }
        return issues;
    }

    private static List<Issue> checkRules(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        List<Object> rules = YamlSafe.asList(doc.rules());
        if (doc.rules() != null && rules == null) {
            issues.add(Issue.structural(Codes.WRONG_SECTION_TYPE,
                    "'Eligibility Rules' must be a YAML list of rule objects.", "section:Eligibility Rules"));
            return issues;
        }
        if (rules == null) {
            return issues;
        }
        for (int i = 0; i < rules.size(); i++) {
            String path = "rules[" + i + "]";
            Object item = rules.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE, "Eligibility Rules entry " + (i + 1) + " must be a mapping.", path));
                continue;
            }
            checkFieldNames(map, RULE_FIELDS, path, issues);
            boolean hasId = checkRequired(map, "id", path, issues);
            if (hasId) {
                Object id = map.get("id");
                if (!(id instanceof String is) || is.isEmpty() || !RULE_ACTION_ID.matcher(is).matches()) {
                    issues.add(Issue.structural(Codes.INVALID_ID,
                            "Rule id must be a nonempty identifier matching [A-Za-z][A-Za-z0-9_-]{0,63}.", path + ".id"));
                }
            }
            // conditions: required nonempty list of conditions
            Object conditions = map.get("conditions");
            List<Object> condList = YamlSafe.asList(conditions);
            if (!checkRequired(map, "conditions", path, issues)) {
                // already reported
            } else if (condList == null || condList.isEmpty()) {
                issues.add(Issue.structural(Codes.EMPTY_REQUIRED_LIST,
                        "Rule 'conditions' must be a nonempty list of conditions.", path + ".conditions"));
            } else {
                for (int c = 0; c < condList.size(); c++) {
                    String cpath = path + ".conditions[" + c + "]";
                    Object cItem = condList.get(c);
                    if (!(cItem instanceof Map<?, ?> cmap)) {
                        issues.add(Issue.structural(Codes.INVALID_TYPE, "Condition " + (c + 1) + " must be a mapping.", cpath));
                        continue;
                    }
                    checkRequired(cmap, "input", cpath, issues);
                    checkRequired(cmap, "op", cpath, issues);
                    checkRequired(cmap, "value", cpath, issues);
                    checkFieldNames(cmap, CONDITION_FIELDS, cpath, issues);
                    Object op = cmap.get("op");
                    if (op instanceof String ops && !OPS.contains(ops)) {
                        issues.add(Issue.structural(Codes.INVALID_ENUM,
                                "Condition op must be one of: eq, gt, lte.", cpath + ".op"));
                    }
                    Object input = cmap.get("input");
                    if (input instanceof String is && is.isEmpty()) {
                        issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                                "Condition input must be a nonempty input name.", cpath + ".input"));
                    }
                }
            }
            // action_ids: required nonempty list
            Object actionIds = map.get("action_ids");
            List<Object> aidList = YamlSafe.asList(actionIds);
            if (!checkRequired(map, "action_ids", path, issues)) {
                // already reported
            } else if (aidList == null || aidList.isEmpty()) {
                issues.add(Issue.structural(Codes.EMPTY_REQUIRED_LIST,
                        "Rule 'action_ids' must be a nonempty list of action ids.", path + ".action_ids"));
            } else {
                for (int a = 0; a < aidList.size(); a++) {
                    Object aid = aidList.get(a);
                    if (aid != null && !(aid instanceof String)) {
                        issues.add(Issue.structural(Codes.INVALID_TYPE,
                                "action_ids entries must be strings.", path + ".action_ids[" + a + "]"));
                    }
                }
            }
        }
        return issues;
    }

    private static List<Issue> checkActions(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        List<Object> actions = YamlSafe.asList(doc.actions());
        if (doc.actions() != null && actions == null) {
            issues.add(Issue.structural(Codes.WRONG_SECTION_TYPE,
                    "'Actions' must be a YAML list of action objects.", "section:Actions"));
            return issues;
        }
        if (actions == null) {
            return issues;
        }
        for (int i = 0; i < actions.size(); i++) {
            String path = "actions[" + i + "]";
            Object item = actions.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE, "Actions entry " + (i + 1) + " must be a mapping.", path));
                continue;
            }
            checkFieldNames(map, ACTION_FIELDS, path, issues);
            checkRequired(map, "id", path, issues);
            boolean hasKind = checkRequired(map, "kind", path, issues);
            boolean hasDesc = checkRequired(map, "description", path, issues);
            String kind = null;
            if (hasKind) {
                Object kv = map.get("kind");
                if (!(kv instanceof String ks) || !ACTION_KINDS.contains(ks)) {
                    issues.add(Issue.structural(Codes.INVALID_ENUM,
                            "Action kind must be one of: refund, escalate, human_assist.", path + ".kind"));
                } else {
                    kind = ks;
                }
            }
            if (hasDesc) {
                Object dv = map.get("description");
                if (!(dv instanceof String ds) || ds.isEmpty()) {
                    issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                            "Action description must be nonempty text.", path + ".description"));
                }
            }
            if (map.containsKey("max_amount")) {
                Object ma = map.get("max_amount");
                if (!isFiniteNumber(ma)) {
                    issues.add(Issue.structural(Codes.INVALID_TYPE,
                            "max_amount must be a positive finite number.", path + ".max_amount"));
                } else if (kind != null && !kind.equals("refund")) {
                    issues.add(Issue.structural(Codes.INVALID_TYPE,
                            "max_amount is only allowed on refund actions, not on '" + kind + "'.", path + ".max_amount"));
                } else if (kind == null || kind.equals("refund")) {
                    if (!isPositiveNumber(ma)) {
                        issues.add(Issue.structural(Codes.INVALID_TYPE,
                                kind == null ? "max_amount must be a positive finite number."
                                        : "A refund action requires a positive finite max_amount.",
                                path + ".max_amount"));
                    }
                }
            }
        }
        return issues;
    }

    private static List<Issue> checkBoundaries(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        Object boundaries = doc.boundaries();
        Map<?, ?> bmap = YamlSafe.asMapping(boundaries);
        if (boundaries != null && bmap == null) {
            issues.add(Issue.structural(Codes.WRONG_SECTION_TYPE,
                    "'Boundaries' must be a YAML mapping containing 'escalation'.", "section:Boundaries"));
            return issues;
        }
        if (bmap == null) {
            return issues;
        }
        checkFieldNames(bmap, BOUNDARY_FIELDS, "boundaries", issues);
        if (!bmap.containsKey("escalation")) {
            issues.add(Issue.structural(Codes.MISSING_FIELD,
                    "Boundaries must define 'escalation'.", "boundaries.escalation"));
            return issues;
        }
        Object esc = bmap.get("escalation");
        List<Object> escList = YamlSafe.asList(esc);
        if (escList == null) {
            issues.add(Issue.structural(Codes.INVALID_TYPE,
                    "Boundaries.escalation must be a (possibly empty) list.", "boundaries.escalation"));
            return issues;
        }
        for (int i = 0; i < escList.size(); i++) {
            String path = "boundaries.escalation[" + i + "]";
            Object item = escList.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE, "Escalation " + (i + 1) + " must be a mapping.", path));
                continue;
            }
            checkFieldNames(map, ESCALATION_FIELDS, path, issues);
            checkRequired(map, "action_id", path, issues);
            checkRequired(map, "input", path, issues);
            checkRequired(map, "op", path, issues);
            checkRequired(map, "amount", path, issues);
            checkRequired(map, "target_action_id", path, issues);
            Object amount = map.get("amount");
            if (amount != null && !isFiniteNumber(amount)) {
                issues.add(Issue.structural(Codes.INVALID_TYPE,
                        "Escalation amount must be a positive finite number.", path + ".amount"));
            }
            Object op = map.get("op");
            if (op instanceof String os && !os.equals("gt")) {
                issues.add(Issue.structural(Codes.INVALID_ENUM,
                        "Escalation op must be 'gt'.", path + ".op"));
            }
        }
        return issues;
    }

    private static List<Issue> checkMessages(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        Object messages = doc.customerMessages();
        Map<?, ?> m = YamlSafe.asMapping(messages);
        if (messages != null && m == null) {
            issues.add(Issue.structural(Codes.WRONG_SECTION_TYPE,
                    "'Customer Messages' must be a YAML mapping with 'primary' and 'escalation'.", "section:Customer Messages"));
            return issues;
        }
        if (m == null) {
            return issues;
        }
        checkFieldNames(m, MESSAGE_FIELDS, "customer_messages", issues);
        for (String field : MESSAGE_FIELDS) {
            String path = "customer_messages." + field;
            if (!m.containsKey(field)) {
                issues.add(Issue.structural(Codes.MISSING_FIELD,
                        "Customer Messages must define '" + field + "'.", path));
                continue;
            }
            Object v = m.get(field);
            if (!(v instanceof String vs) || vs.isEmpty()) {
                issues.add(Issue.structural(Codes.EMPTY_REQUIRED_TEXT,
                        "Customer message '" + field + "' must be nonempty text.", path));
            }
        }
        return issues;
    }

    // ---- helpers ----

    private static void checkFieldNames(Map<?, ?> map, Set<String> allowed, String path, List<Issue> issues) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String ks) || !allowed.contains(ks)) {
                issues.add(Issue.structural(Codes.UNKNOWN_FIELD,
                        "The field '" + key + "' is not supported here.", path + "." + key));
            }
        }
    }

    private static boolean checkRequired(Map<?, ?> map, String field, String path, List<Issue> issues) {
        if (!map.containsKey(field)) {
            issues.add(Issue.structural(Codes.MISSING_FIELD,
                    "The field '" + field + "' is required.", path + "." + field));
            return false;
        }
        return true;
    }

    private static String stringValue(Object value) {
        return value instanceof String s ? s : null;
    }

    private static boolean isFiniteNumber(Object value) {
        if (value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return true;
        }
        if (value instanceof Double d) {
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }
        if (value instanceof Float f) {
            return !Float.isNaN(f) && !Float.isInfinite(f);
        }
        return false;
    }

    private static boolean isPositiveNumber(Object value) {
        if (!isFiniteNumber(value)) {
            return false;
        }
        double d;
        if (value instanceof BigDecimal bd) {
            d = bd.doubleValue();
        } else if (value instanceof BigInteger bi) {
            d = bi.doubleValue();
        } else {
            d = ((Number) value).doubleValue();
        }
        return d > 0;
    }
}
