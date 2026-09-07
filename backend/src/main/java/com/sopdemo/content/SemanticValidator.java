package com.sopdemo.content;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic validation (spec.md section 2: references/uniqueness/operators; FR-030).
 * Runs only after structural validation passes; all discovered issues are returned (FR-034).
 */
final class SemanticValidator {

    private SemanticValidator() {
    }

    record InputInfo(String name, String type) {
    }

    record ActionInfo(int index, String id, String kind) {
    }

    record RuleInfo(int index, String id) {
    }

    static final class Context {
        Map<String, InputInfo> inputs = new HashMap<>();
        Map<String, ActionInfo> actions = new HashMap<>();
        Map<String, RuleInfo> rules = new HashMap<>();
        List<InputInfo> inputOrder = new ArrayList<>();
        List<ActionInfo> actionOrder = new ArrayList<>();
    }

    static Context buildContext(ParsedDoc doc, List<Issue> issues) {
        Context ctx = new Context();
        List<Object> inputs = YamlSafe.asList(doc.inputs());
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) instanceof Map<?, ?> map) {
                    String name = str(map.get("name"));
                    String type = str(map.get("type"));
                    if (name != null) {
                        if (ctx.inputs.containsKey(name)) {
                            issues.add(Issue.semantic(Codes.DUPLICATE_ID,
                                    "Input names must be unique; '" + name + "' is declared more than once.",
                                    "inputs[" + i + "].name"));
                        } else {
                            ctx.inputs.put(name, new InputInfo(name, type));
                            ctx.inputOrder.add(ctx.inputs.get(name));
                        }
                    }
                }
            }
        }
        List<Object> actions = YamlSafe.asList(doc.actions());
        if (actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i) instanceof Map<?, ?> map) {
                    String id = str(map.get("id"));
                    String kind = str(map.get("kind"));
                    if (id != null) {
                        if (ctx.actions.containsKey(id)) {
                            issues.add(Issue.semantic(Codes.DUPLICATE_ID,
                                    "Action ids must be unique; '" + id + "' is declared more than once.",
                                    "actions[" + i + "].id"));
                        } else {
                            ctx.actions.put(id, new ActionInfo(i, id, kind));
                            ctx.actionOrder.add(ctx.actions.get(id));
                        }
                    }
                }
            }
        }
        List<Object> rules = YamlSafe.asList(doc.rules());
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i) instanceof Map<?, ?> map) {
                    String id = str(map.get("id"));
                    if (id != null) {
                        if (ctx.rules.containsKey(id)) {
                            issues.add(Issue.semantic(Codes.DUPLICATE_ID,
                                    "Rule ids must be unique; '" + id + "' is declared more than once.",
                                    "rules[" + i + "].id"));
                        } else {
                            ctx.rules.put(id, new RuleInfo(i, id));
                        }
                    }
                }
            }
        }
        return ctx;
    }

    static List<Issue> validate(ParsedDoc doc) {
        List<Issue> issues = new ArrayList<>();
        checkNonEmpty(doc, issues);
        Context ctx = buildContext(doc, issues);
        issues.addAll(validateOnly(doc, ctx));
        return issues;
    }

    static List<Issue> validateOnly(ParsedDoc doc, Context ctx) {
        List<Issue> issues = new ArrayList<>();
        checkConditions(doc, ctx, issues);
        checkActionReferences(doc, ctx, issues);
        checkEscalations(doc, ctx, issues);
        return issues;
    }

    static void checkNonEmpty(ParsedDoc doc, List<Issue> issues) {
        if (YamlSafe.asList(doc.inputs()) == null || YamlSafe.asList(doc.inputs()).isEmpty()) {
            issues.add(Issue.semantic(Codes.EMPTY_REQUIRED_LIST,
                    "The SOP requires at least one input.", "section:Inputs Required"));
        }
        if (YamlSafe.asList(doc.rules()) == null || YamlSafe.asList(doc.rules()).isEmpty()) {
            issues.add(Issue.semantic(Codes.EMPTY_REQUIRED_LIST,
                    "The SOP requires at least one eligibility rule.", "section:Eligibility Rules"));
        }
        if (YamlSafe.asList(doc.actions()) == null || YamlSafe.asList(doc.actions()).isEmpty()) {
            issues.add(Issue.semantic(Codes.EMPTY_REQUIRED_LIST,
                    "The SOP requires at least one action.", "section:Actions"));
        }
    }

    private static void checkConditions(ParsedDoc doc, Context ctx, List<Issue> issues) {
        List<Object> rules = YamlSafe.asList(doc.rules());
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            if (!(rules.get(i) instanceof Map<?, ?> map)) {
                continue;
            }
            if (!(map.get("conditions") instanceof List<?> conditions)) {
                continue;
            }
            for (int c = 0; c < conditions.size(); c++) {
                String path = "rules[" + i + "].conditions[" + c + "]";
                if (!(conditions.get(c) instanceof Map<?, ?> cmap)) {
                    continue;
                }
                Object opObj = cmap.get("op");
                Object inputObj = cmap.get("input");
                Object valueObj = cmap.get("value");
                String op = str(opObj);
                String inputName = str(inputObj);
                InputInfo info = inputName == null ? null : ctx.inputs.get(inputName);
                boolean valueIsBool = valueObj instanceof Boolean;
                boolean valueIsNumber = isFiniteNumber(valueObj);
                if (info == null) {
                    if (inputName != null && !inputName.isEmpty()) {
                        issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                                "Condition input '" + inputName + "' is not a declared input.", path + ".input"));
                    } else {
                        continue; // structural problem already reported
                    }
                } else if (info.type() != null) {
                    if (info.type().equals("number")) {
                        if (!valueIsNumber) {
                            issues.add(Issue.semantic(Codes.TYPE_MISMATCH,
                                    "Condition value for numeric input '" + inputName + "' must be a number.",
                                    path + ".value"));
                        }
                    } else if (info.type().equals("boolean")) {
                        if (!valueIsBool) {
                            issues.add(Issue.semantic(Codes.TYPE_MISMATCH,
                                    "Condition value for boolean input '" + inputName + "' must be true or false.",
                                    path + ".value"));
                        }
                        if (op != null && !op.equals("eq")) {
                            issues.add(Issue.semantic(Codes.OPERATOR_MISMATCH,
                                    "Boolean inputs only support the 'eq' operator.", path + ".op"));
                        }
                    }
                }
            }
        }
    }

    private static void checkActionReferences(ParsedDoc doc, Context ctx, List<Issue> issues) {
        List<Object> rules = YamlSafe.asList(doc.rules());
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            if (!(rules.get(i) instanceof Map<?, ?> map)) {
                continue;
            }
            if (!(map.get("action_ids") instanceof List<?> ids)) {
                continue;
            }
            for (int a = 0; a < ids.size(); a++) {
                Object idObj = ids.get(a);
                if (!(idObj instanceof String id) || id.isEmpty()) {
                    continue; // structural problem already reported
                }
                if (!ctx.actions.containsKey(id)) {
                    issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                            "action_ids entry '" + id + "' is not a declared action id.",
                            "rules[" + i + "].action_ids[" + a + "]"));
                }
            }
        }
    }

    private static void checkEscalations(ParsedDoc doc, Context ctx, List<Issue> issues) {
        Map<?, ?> bmap = YamlSafe.asMapping(doc.boundaries());
        if (bmap == null || !(bmap.get("escalation") instanceof List<?> escalation)) {
            return;
        }
        for (int i = 0; i < escalation.size(); i++) {
            if (!(escalation.get(i) instanceof Map<?, ?> map)) {
                continue;
            }
            String path = "boundaries.escalation[" + i + "]";
            Object actionId = map.get("action_id");
            Object input = map.get("input");
            Object amount = map.get("amount");
            Object target = map.get("target_action_id");

            if (actionId instanceof String aid && !aid.isEmpty() && !ctx.actions.containsKey(aid)) {
                issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                        "Escalation action_id '" + aid + "' is not a declared action.", path + ".action_id"));
            } else if (actionId instanceof String aid && ctx.actions.containsKey(aid)) {
                ActionInfo ai = ctx.actions.get(aid);
                if (ai.kind() != null && !ai.kind().equals("refund")) {
                    issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                            "Escalation action_id must reference a refund action; '" + aid + "' is a '" + ai.kind() + "'.",
                            path + ".action_id"));
                }
            }

            if (input instanceof String in && !in.isEmpty() && !ctx.inputs.containsKey(in)) {
                issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                        "Escalation input '" + in + "' is not a declared input.", path + ".input"));
            } else if (input instanceof String in && ctx.inputs.containsKey(in)) {
                InputInfo info = ctx.inputs.get(in);
                if (info.type() != null && !info.type().equals("number")) {
                    issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                            "Escalation input '" + in + "' must be a numeric input.", path + ".input"));
                }
            }

            if (amount != null && isFiniteNumber(amount) && toDouble(amount) <= 0) {
                issues.add(Issue.semantic(Codes.INVALID_TYPE,
                        "Escalation amount must be greater than zero.", path + ".amount"));
            }

            if (target instanceof String ta && !ta.isEmpty() && !ctx.actions.containsKey(ta)) {
                issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                        "Escalation target_action_id '" + ta + "' is not a declared action.", path + ".target_action_id"));
            } else if (target instanceof String ta && ctx.actions.containsKey(ta)) {
                ActionInfo ai = ctx.actions.get(ta);
                if (ai.kind() != null && !ai.kind().equals("escalate")) {
                    issues.add(Issue.semantic(Codes.BAD_REFERENCE,
                            "Escalation target_action_id must reference an escalate action; '" + ta + "' is a '" + ai.kind() + "'.",
                            path + ".target_action_id"));
                }
            }
        }
    }

    // ---- shared helpers ----

    static String str(Object value) {
        return value instanceof String s ? s : null;
    }

    static boolean isFiniteNumber(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return true;
        }
        if (value instanceof BigInteger) {
            return true;
        }
        if (value instanceof BigDecimal bd) {
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

    static double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }
}
