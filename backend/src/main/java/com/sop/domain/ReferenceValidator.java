package com.sop.domain;

import com.sop.dto.Issue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-section reference, uniqueness, condition-type and financial-safety checks
 * (DES-005 step 7/8, FR-030, FR-032). Operates over the parsed machine values.
 */
public final class ReferenceValidator {

  private ReferenceValidator() {}

  public static void validate(ParsedDocument doc, List<Issue> issues) {
    Map<String, Object> mv = doc.machineValues;
    if (mv.isEmpty()) {
      return; // structural issues already reported; nothing to reference
    }

    List<Object> inputs = asList(mv.get(Contract.SEC_INPUTS));
    List<Object> rules = asList(mv.get(Contract.SEC_RULES));
    List<Object> actions = asList(mv.get(Contract.SEC_ACTIONS));
    Object boundaries = mv.get(Contract.SEC_BOUNDARIES);

    // ---- collect names / ids ----
    Set<String> inputNames = new LinkedHashSet<>();
    Set<String> booleanInputs = new HashSet<>();
    Set<String> numberInputs = new HashSet<>();
    collectInputs(inputs, inputNames, booleanInputs, numberInputs);

    Set<String> actionIds = new LinkedHashSet<>();
    Set<String> refundActions = new LinkedHashSet<>();
    Set<String> escalateActions = new LinkedHashSet<>();
    Map<String, Object> actionMaxAmount = new LinkedHashMap<>();
    collectActions(actions, actionIds, refundActions, escalateActions, actionMaxAmount);

    // ---- uniqueness ----
    checkUniqueness(inputs, "name", Contract.C_DUPLICATE_INPUT_NAME, "inputs", issues);
    checkUniqueness(rules, "id", Contract.C_DUPLICATE_RULE_ID, "rules", issues);
    checkUniqueness(actions, "id", Contract.C_DUPLICATE_ACTION_ID, "actions", issues);

    // ---- rule conditions references ----
    for (int r = 0; r < rules.size(); r++) {
      if (!(rules.get(r) instanceof Map<?, ?> rule)) continue;
      String rpath = "rules[" + r + "]";

      Object actionIdsList = rule.get("action_ids");
      if (actionIdsList instanceof List<?> ail) {
        for (int j = 0; j < ail.size(); j++) {
          Object aid = ail.get(j);
          if (!(aid instanceof String as) || !actionIds.contains(as)) {
            issues.add(Issue.semantic(Contract.C_REF_ACTION_UNKNOWN,
                "rule action `" + aid + "` is not a declared action",
                rpath + ".action_ids[" + j + "]"));
          }
        }
      }

      Object conditions = rule.get("conditions");
      if (!(conditions instanceof List<?> cl)) continue;
      for (int c = 0; c < cl.size(); c++) {
        if (!(cl.get(c) instanceof Map<?, ?> cond)) continue;
        String cpath = rpath + ".conditions[" + c + "]";
        Object in = cond.get("input");
        Object op = cond.get("op");
        Object val = cond.get("value");

        if (!(in instanceof String ins) || !inputNames.contains(ins)) {
          issues.add(Issue.semantic(Contract.C_REF_INPUT_UNKNOWN,
              "condition input `" + in + "` is not a declared input", cpath + ".input"));
          continue;
        }
        boolean isBool = booleanInputs.contains(ins);
        boolean isNum = numberInputs.contains(ins);

        if (isBool && op instanceof String os && !"eq".equals(os)) {
          issues.add(Issue.semantic(Contract.C_REF_CONDITION_OP,
              "boolean input `" + ins + "` supports only `eq`", cpath + ".op"));
        }

        if (isBool) {
          if (!(val instanceof Boolean)) {
            issues.add(Issue.semantic(Contract.C_REF_CONDITION_TYPE,
                "boolean input `" + ins + "` requires a boolean value", cpath + ".value"));
          }
        } else if (isNum) {
          if (!Types.isFiniteNumber(val)) {
            issues.add(Issue.semantic(Contract.C_REF_CONDITION_TYPE,
                "numeric input `" + ins + "` requires a finite number value", cpath + ".value"));
          }
        }
      }
    }

    // ---- refund / answer_question invariants ----
    String intent = doc.frontMatter.get("intent") instanceof String s ? s : null;
    if (Contract.INTENT_REFUND.equals(intent)) {
      validateRefundPolicy(refundActions, numberInputs, actionMaxAmount,
          escalateActions, boundaries, issues);
    } else if (Contract.INTENT_QUESTION.equals(intent)) {
      if (!refundActions.isEmpty()) {
        issues.add(Issue.semantic(Contract.C_QUESTION_REFUND_NOT_ALLOWED,
            "answer_question must not declare refund actions", "actions"));
      }
    }
  }

  private static void validateRefundPolicy(
      Set<String> refundActions, Set<String> numberInputs,
      Map<String, Object> actionMaxAmount, Set<String> escalateActions,
      Object boundaries, List<Issue> issues) {

    // exactly one refund action
    if (refundActions.isEmpty()) {
      issues.add(Issue.semantic(Contract.C_FIN_REFUND_MISSING,
          "refund_duplicate_charge requires exactly one refund action", "actions"));
    } else if (refundActions.size() > 1) {
      issues.add(Issue.semantic(Contract.C_REFUND_ACTION_MULTIPLE,
          "refund_duplicate_charge requires exactly one refund action", "actions"));
    }

    // refund_amount numeric input
    if (!numberInputs.contains("refund_amount")) {
      issues.add(Issue.semantic(Contract.C_FIN_REFUND_AMOUNT_INPUT,
          "a numeric input named `refund_amount` is required", "inputs"));
    }

    if (refundActions.size() != 1) {
      return; // limit / boundary cannot be evaluated without exactly one refund action
    }

    String refundAction = refundActions.iterator().next();
    Object maxAmount = actionMaxAmount.get(refundAction);
    boolean limitOk = Types.isPositiveFiniteNumber(maxAmount);

    List<Object> escalations = (boundaries instanceof Map<?, ?> b)
        ? asList(b.get("escalation")) : new ArrayList<>();

    boolean sawMatchingEscalation = false;
    boolean sawAnyRelevant = false;
    for (int i = 0; i < escalations.size(); i++) {
      if (!(escalations.get(i) instanceof Map<?, ?> esc)) continue;
      String epath = "boundaries.escalation[" + i + "]";
      Object eid = esc.get("action_id");
      if (!(eid instanceof String eids) || !refundAction.equals(eids)) {
        continue; // not about this refund action
      }
      sawAnyRelevant = true;
      boolean inputIsRefundAmount = "refund_amount".equals(String.valueOf(esc.get("input")))
          && numberInputs.contains("refund_amount");
      boolean opOk = "gt".equals(String.valueOf(esc.get("op")));
      boolean amountEqualsLimit = limitOk
          && Types.isPositiveFiniteNumber(esc.get("amount"))
          && Types.sameNumber(esc.get("amount"), maxAmount);
      boolean targetEscalate = esc.get("target_action_id") instanceof String t
          && escalateActions.contains(t);

      if (inputIsRefundAmount && opOk && amountEqualsLimit && targetEscalate) {
        sawMatchingEscalation = true;
        return;
      }

      if (!amountEqualsLimit) {
        issues.add(Issue.semantic(Contract.C_FIN_ESCALATION_BOUND,
            "escalation `amount` must equal the refund limit"
                + (limitOk ? " (" + maxAmount + ")" : ""), epath + ".amount"));
      }
      if (!targetEscalate) {
        issues.add(Issue.semantic(Contract.C_FIN_ESCALATION_BOUND,
            "escalation must target an existing `escalate` action", epath + ".target_action_id"));
      }
      if (!opOk) {
        issues.add(Issue.semantic(Contract.C_FIN_ESCALATION_BOUND,
            "escalation `op` must be `gt`", epath + ".op"));
      }
      if (!inputIsRefundAmount) {
        issues.add(Issue.semantic(Contract.C_FIN_ESCALATION_BOUND,
            "escalation `input` must be the numeric input `refund_amount`", epath + ".input"));
      }
    }

    if (!sawAnyRelevant) {
      issues.add(Issue.semantic(Contract.C_FIN_ESCALATION_MISSING,
          "an escalation boundary naming the refund action is required",
          "boundaries.escalation"));
    }
  }

  // ---- collection helpers ----

  private static void collectInputs(List<Object> inputs, Set<String> names,
                                    Set<String> bools, Set<String> nums) {
    for (Object o : inputs) {
      if (!(o instanceof Map<?, ?> m)) continue;
      if (m.get("name") instanceof String n) {
        names.add(n);
        Object type = m.get("type");
        if ("boolean".equals(type)) bools.add(n);
        if ("number".equals(type)) nums.add(n);
      }
    }
  }

  private static void collectActions(List<Object> actions, Set<String> ids,
                                     Set<String> refunds, Set<String> escalates,
                                     Map<String, Object> maxAmount) {
    for (Object o : actions) {
      if (!(o instanceof Map<?, ?> m)) continue;
      if (m.get("id") instanceof String id) {
        ids.add(id);
        if ("refund".equals(m.get("kind"))) {
          refunds.add(id);
          if (m.get("max_amount") != null) maxAmount.put(id, m.get("max_amount"));
        }
        if ("escalate".equals(m.get("kind"))) escalates.add(id);
      }
    }
  }

  private static void checkUniqueness(List<Object> list, String field, String code,
                                      String path, List<Issue> issues) {
    Set<Object> seen = new HashSet<>();
    for (int i = 0; i < list.size(); i++) {
      if (!(list.get(i) instanceof Map<?, ?> m)) continue;
      Object val = m.get(field);
      if (val != null && !seen.add(val)) {
        issues.add(Issue.structural(code,
            "duplicate `" + field + "` value `" + val + "`", path + "[" + i + "]." + field));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object v) {
    if (v instanceof List<?> l) return (List<Object>) l;
    return new ArrayList<>();
  }
}
