package com.sop.domain;

import com.sop.dto.Issue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-field type/shape validation for the five machine sections (DES-005 step 5/6).
 * Emits structural issues with stable paths. Rejects unknown keys at every level.
 */
public final class ShapeValidator {

  private ShapeValidator() {}

  public static void validateInputs(Object value, List<Issue> issues) {
    String path = "inputs";
    if (!(value instanceof List<?> list)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "`Inputs Required` must be a list of inputs", path));
      return;
    }
    if (list.isEmpty()) {
      issues.add(Issue.semantic(Contract.C_EMPTY_INPUTS,
          "at least one input is required", path));
    }
    for (int i = 0; i < list.size(); i++) {
      validateInput(list.get(i), path + "[" + i + "]", issues);
    }
  }

  private static void validateInput(Object value, String path, List<Issue> issues) {
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE, "input must be an object", path));
      return;
    }
    Set<String> unknown = unknownKeys(m, Set.of("name", "type"), path, issues);
    Object name = m.get("name");
    if (!(name instanceof String ns) || ns.isEmpty() || !Contract.INPUT_NAME.matcher(ns).matches()) {
      issues.add(Issue.structural(
          (name instanceof String) ? Contract.C_FIELD_VALUE : (name == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "input `name` must match [a-z][a-z0-9_]{0,63}", path + ".name"));
    }
    Object type = m.get("type");
    if (!(type instanceof String ts) || ts.isEmpty() || !Contract.INPUT_TYPES.contains(ts)) {
      issues.add(Issue.structural(
          (type instanceof String) ? Contract.C_FIELD_VALUE : (type == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "input `type` must be `number` or `boolean`", path + ".type"));
    }
  }

  public static void validateRules(Object value, List<Issue> issues) {
    String path = "rules";
    if (!(value instanceof List<?> list)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "`Eligibility Rules` must be a list of rules", path));
      return;
    }
    if (list.isEmpty()) {
      issues.add(Issue.semantic(Contract.C_EMPTY_RULES, "at least one rule is required", path));
    }
    for (int i = 0; i < list.size(); i++) {
      validateRule(list.get(i), path + "[" + i + "]", issues);
    }
  }

  private static void validateRule(Object value, String path, List<Issue> issues) {
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE, "rule must be an object", path));
      return;
    }
    unknownKeys(m, Set.of("id", "conditions", "action_ids"), path, issues);
    Object id = m.get("id");
    if (!(id instanceof String is) || is.isEmpty() || !Contract.IDENTIFIER.matcher(is).matches()) {
      issues.add(Issue.structural(
          (id instanceof String) ? Contract.C_FIELD_VALUE : (id == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "rule `id` must be a nonempty identifier", path + ".id"));
    }
    Object conditions = m.get("conditions");
    if (!(conditions instanceof List<?> cl)) {
      issues.add(Issue.structural(
          (conditions instanceof List<?>) ? Contract.C_FIELD_VALUE
              : (conditions == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "rule `conditions` must be a list", path + ".conditions"));
    } else {
      if (cl.isEmpty()) {
        issues.add(Issue.structural(Contract.C_FIELD_VALUE,
            "rule `conditions` must be a nonempty list", path + ".conditions"));
      }
      for (int c = 0; c < cl.size(); c++) {
        validateCondition(cl.get(c), path + ".conditions[" + c + "]", issues);
      }
    }
    Object actionIds = m.get("action_ids");
    if (!(actionIds instanceof List<?> al)) {
      issues.add(Issue.structural(
          (actionIds instanceof List<?>) ? Contract.C_FIELD_VALUE
              : (actionIds == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "rule `action_ids` must be a list", path + ".action_ids"));
    } else {
      if (al.isEmpty()) {
        issues.add(Issue.structural(Contract.C_FIELD_VALUE,
            "rule `action_ids` must be a nonempty list", path + ".action_ids"));
      }
    }
  }

  private static void validateCondition(Object value, String path, List<Issue> issues) {
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE, "condition must be an object", path));
      return;
    }
    unknownKeys(m, Set.of("input", "op", "value"), path, issues);
    checkString(issues, m.get("input"), "input", path + ".input");
    Object op = m.get("op");
    if (!(op instanceof String os) || os.isEmpty() || !Contract.OPS.contains(os)) {
      issues.add(Issue.structural(
          (op instanceof String) ? Contract.C_FIELD_VALUE
              : (op == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "condition `op` must be `eq`, `gt`, or `lte`", path + ".op"));
    }
    Object v = m.get("value");
    if (v == null) {
      issues.add(Issue.structural(Contract.C_FIELD_MISSING,
          "condition `value` is required", path + ".value"));
    } else if (!Types.isNumber(v) && !(v instanceof Boolean)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "condition `value` must be a number or boolean", path + ".value"));
    }
  }

  public static void validateActions(Object value, List<Issue> issues) {
    String path = "actions";
    if (!(value instanceof List<?> list)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "`Actions` must be a list of actions", path));
      return;
    }
    if (list.isEmpty()) {
      issues.add(Issue.semantic(Contract.C_EMPTY_ACTIONS, "at least one action is required", path));
    }
    for (int i = 0; i < list.size(); i++) {
      validateAction(list.get(i), path + "[" + i + "]", issues);
    }
  }

  private static void validateAction(Object value, String path, List<Issue> issues) {
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE, "action must be an object", path));
      return;
    }
    unknownKeys(m, Set.of("id", "kind", "description", "max_amount"), path, issues);
    checkId(issues, m.get("id"), path + ".id");
    Object kind = m.get("kind");
    if (!(kind instanceof String ks) || ks.isEmpty() || !Contract.ACTION_KINDS.contains(ks)) {
      issues.add(Issue.structural(
          (kind instanceof String) ? Contract.C_FIELD_VALUE : (kind == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "action `kind` must be `refund`, `escalate`, or `human_assist`", path + ".kind"));
    }
    Object desc = m.get("description");
    if (!(desc instanceof String ds) || ds.isBlank()) {
      issues.add(Issue.structural(
          (desc instanceof String) ? Contract.C_FIELD_EMPTY : (desc == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "action `description` must be nonempty text", path + ".description"));
    }
    // max_amount: required for refund, disallowed otherwise — checked here for type only
    Object maxAmount = m.get("max_amount");
    boolean isRefund = "refund".equals(kind);
    if (maxAmount != null && !Types.isNumber(maxAmount)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "action `max_amount` must be a number", path + ".max_amount"));
    }
    if (isRefund) {
      if (maxAmount == null) {
        issues.add(Issue.semantic(Contract.C_FIN_REFUND_MAX_AMOUNT,
            "refund action requires a positive numeric `max_amount`", path + ".max_amount"));
      } else if (!Types.isPositiveFiniteNumber(maxAmount)) {
        issues.add(Issue.semantic(Contract.C_FIN_REFUND_MAX_AMOUNT,
            "refund action `max_amount` must be a positive finite number", path + ".max_amount"));
      }
    } else if (maxAmount != null) {
      issues.add(Issue.structural(Contract.C_FIELD_UNKNOWN,
          "`max_amount` is disallowed for non-refund actions", path + ".max_amount"));
    }
  }

  public static void validateBoundaries(Object value, List<Issue> issues) {
    String path = "boundaries";
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "`Boundaries` must be an object", path));
      return;
    }
    Set<String> unknown = unknownKeys(m, Set.of("escalation"), path, issues);
    Object escalation = m.get("escalation");
    if (!(escalation instanceof List<?> el)) {
      issues.add(Issue.structural(
          (escalation instanceof List<?>) ? Contract.C_FIELD_VALUE
              : (escalation == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "boundaries `escalation` must be a list", path + ".escalation"));
      return;
    }
    for (int i = 0; i < el.size(); i++) {
      validateEscalation(el.get(i), path + ".escalation[" + i + "]", issues);
    }
  }

  private static void validateEscalation(Object value, String path, List<Issue> issues) {
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE, "escalation must be an object", path));
      return;
    }
    unknownKeys(m, Set.of("action_id", "input", "op", "amount", "target_action_id"), path, issues);
    checkId(issues, m.get("action_id"), path + ".action_id");
    checkString(issues, m.get("input"), "input", path + ".input");
    Object op = m.get("op");
    if (!(op instanceof String os) || !"gt".equals(os)) {
      issues.add(Issue.structural(
          (op instanceof String) ? Contract.C_FIELD_VALUE : (op == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "escalation `op` must be `gt`", path + ".op"));
    }
    Object amount = m.get("amount");
    if (!Types.isPositiveFiniteNumber(amount)) {
      issues.add(Issue.structural(Contract.C_FIN_ESCALATION_BOUND,
          "escalation `amount` must be a positive finite number", path + ".amount"));
    }
    checkId(issues, m.get("target_action_id"), path + ".target_action_id");
  }

  public static void validateMessages(Object value, List<Issue> issues) {
    String path = "customer_messages";
    if (!(value instanceof Map<?, ?> m)) {
      issues.add(Issue.structural(Contract.C_FIELD_TYPE,
          "`Customer Messages` must be an object", path));
      return;
    }
    unknownKeys(m, Set.of("primary", "escalation"), path, issues);
    for (String key : List.of("primary", "escalation")) {
      Object v = m.get(key);
      if (!(v instanceof String s) || s.isBlank()) {
        issues.add(Issue.structural(
            (v instanceof String) ? Contract.C_FIELD_EMPTY
                : (v == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
            "customer message `" + key + "` must be a nonempty string", path + "." + key));
      }
    }
  }

  // ---- helpers ----

  private static Set<String> unknownKeys(Map<?, ?> m, Set<String> allowed, String path, List<Issue> issues) {
    Set<String> unknown = new LinkedHashSet<>();
    for (Object key : m.keySet()) {
      if (key instanceof String ks && !allowed.contains(ks)) {
        unknown.add(ks);
        issues.add(Issue.structural(Contract.C_FIELD_UNKNOWN,
            "unknown key `" + ks + "`", path + "." + ks));
      }
    }
    return unknown;
  }

  private static void checkString(List<Issue> issues, Object v, String field, String path) {
    if (!(v instanceof String s) || s.isEmpty()) {
      issues.add(Issue.structural(
          (v instanceof String) ? Contract.C_FIELD_VALUE : (v == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "`" + field + "` must be a nonempty string", path));
    } else if (!Contract.IDENTIFIER.matcher(s).matches()) {
      // for `input` this is fine (it must name an input) — the identifier check is only for ids
    }
  }

  private static void checkId(List<Issue> issues, Object v, String path) {
    if (!(v instanceof String s) || s.isEmpty() || !Contract.IDENTIFIER.matcher(s).matches()) {
      issues.add(Issue.structural(
          (v instanceof String) ? Contract.C_FIELD_VALUE : (v == null ? Contract.C_FIELD_MISSING : Contract.C_FIELD_TYPE),
          "must be a nonempty identifier [A-Za-z][A-Za-z0-9_-]{0,63}", path));
    }
  }
}
