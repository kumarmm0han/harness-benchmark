package com.sop.domain;

import com.sop.dto.Issue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Front-matter validation (DES-005 step 4, spec §1). */
public final class FrontMatterValidator {
  private FrontMatterValidator() {}

  public static void validate(Map<String, Object> fm, List<Issue> issues) {
    if (fm == null || fm.isEmpty()) {
      return; // FRONTMATTER_MISSING already reported by the splitter
    }
    // unknown keys
    Set<String> allowed = new HashSet<>(Contract.FRONT_MATTER_KEYS);
    for (String key : fm.keySet()) {
      if (!allowed.contains(key)) {
        issues.add(Issue.structural(Contract.C_FRONTMATTER_UNKNOWN_KEY,
            "unknown front-matter key `" + key + "`", "front_matter." + key));
      }
    }
    // required keys present + string
    for (String key : Contract.FRONT_MATTER_KEYS) {
      if (!fm.containsKey(key)) {
        issues.add(Issue.structural(Contract.C_FIELD_MISSING,
            "front-matter key `" + key + "` is required", "front_matter." + key));
        continue;
      }
      Object v = fm.get(key);
      if (!Types.isNonBlankString(v)) {
        if (v == null) {
          issues.add(Issue.structural(Contract.C_FIELD_MISSING,
              "front-matter key `" + key + "` is required", "front_matter." + key));
        } else if (v instanceof String s && s.isBlank()) {
          issues.add(Issue.structural(Contract.C_FIELD_EMPTY,
              "front-matter key `" + key + "` must be nonempty", "front_matter." + key));
        } else {
          issues.add(Issue.structural(Contract.C_FRONTMATTER_TYPE,
              "front-matter key `" + key + "` must be a string", "front_matter." + key));
        }
      }
    }
    // enum checks (only when the value is a string)
    checkEnum(issues, fm.get("domain"), Contract.C_FRONTMATTER_ENUM, "front_matter.domain",
        d -> Contract.DOMAINS.contains(d));
    checkEnum(issues, fm.get("intent"), Contract.C_FRONTMATTER_ENUM, "front_matter.intent",
        Contract.INTENTS::contains);
    checkEnum(issues, fm.get("risk_level"), Contract.C_FRONTMATTER_ENUM, "front_matter.risk_level",
        Contract.RISKS::contains);
    checkEnum(issues, fm.get("max_autonomy"), Contract.C_FRONTMATTER_ENUM, "front_matter.max_autonomy",
        Contract.AUTONOMY::contains);
    // sop_id format
    Object sopId = fm.get("sop_id");
    if (sopId instanceof String s && !s.isEmpty() && !Contract.SOP_ID.matcher(s).matches()) {
      issues.add(Issue.structural(Contract.C_FRONTMATTER_SOP_ID_FORMAT,
          "sop_id must match [A-Z][A-Z0-9-]{0,63}", "front_matter.sop_id"));
    }
  }

  /** The refund intent requires domain Billing. Reported independently (DES-005 step 4, semantic). */
  public static void validateRefundDomain(Map<String, Object> fm, List<Issue> issues) {
    Object intent = fm.get("intent");
    Object domain = fm.get("domain");
    if (Contract.INTENT_REFUND.equals(intent) && Contract.DOMAINS.contains(domain)
        && !"Billing".equals(domain)) {
      issues.add(Issue.semantic(Contract.C_FRONTMATTER_REFUND_DOMAIN,
          "refund_duplicate_charge requires domain `Billing` but got `" + domain + "`",
          "front_matter.domain"));
    }
  }

  private interface Predicate { boolean test(String value); }

  private static void checkEnum(List<Issue> issues, Object value, String code, String path,
                                Predicate allowed) {
    if (value instanceof String s && !s.isEmpty() && !allowed.test(s)) {
      issues.add(Issue.structural(code, "value `" + s + "` is not supported at " + path, path));
    }
  }
}
