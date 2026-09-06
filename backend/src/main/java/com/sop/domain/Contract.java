package com.sop.domain;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Frozen contract constants (DES-001 / spec §1–§2). */
public final class Contract {
  private Contract() {}

  public static final int SOURCE_BYTES_MAX = 65_536;
  public static final int YAML_MAX_DEPTH = 20;

  public static final List<String> FRONT_MATTER_KEYS =
      List.of("sop_id", "title", "owner_team", "domain", "intent", "risk_level", "max_autonomy");
  public static final Set<String> DOMAINS = Set.of("Billing", "Support");
  public static final Set<String> INTENTS = Set.of("refund_duplicate_charge", "answer_question");
  public static final Set<String> RISKS = Set.of("low", "medium");
  public static final Set<String> AUTONOMY = Set.of("assist");

  public static final Pattern SOP_ID = Pattern.compile("[A-Z][A-Z0-9-]{0,63}");
  public static final Pattern INPUT_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
  public static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

  public static final Set<String> OPS = Set.of("eq", "gt", "lte");
  public static final Set<String> ACTION_KINDS = Set.of("refund", "escalate", "human_assist");
  public static final Set<String> INPUT_TYPES = Set.of("number", "boolean");

  public static final String INTENT_REFUND = "refund_duplicate_charge";
  public static final String INTENT_QUESTION = "answer_question";

  // Issue codes (DES-001)
  public static final String C_PARSE_FAILED = "PARSE_FAILED";
  public static final String C_SOURCE_TOO_LARGE = "SOURCE_TOO_LARGE";
  public static final String C_NESTING_TOO_DEEP = "NESTING_TOO_DEEP";
  public static final String C_NON_FINITE_NUMBER = "NON_FINITE_NUMBER";
  public static final String C_ALIAS = "ALIAS";
  public static final String C_DUPLICATE_KEY = "DUPLICATE_KEY";
  public static final String C_UNKNOWN_TAG = "UNKNOWN_TAG";
  public static final String C_FRONTMATTER_MISSING = "FRONTMATTER_MISSING";
  public static final String C_FRONTMATTER_UNKNOWN_KEY = "FRONTMATTER_UNKNOWN_KEY";
  public static final String C_FRONTMATTER_TYPE = "FRONTMATTER_TYPE";
  public static final String C_FRONTMATTER_ENUM = "FRONTMATTER_ENUM";
  public static final String C_FRONTMATTER_SOP_ID_FORMAT = "FRONTMATTER_SOP_ID_FORMAT";
  public static final String C_FRONTMATTER_EMPTY = "FRONTMATTER_EMPTY";
  public static final String C_FRONTMATTER_REFUND_DOMAIN = "FRONTMATTER_REFUND_DOMAIN";
  public static final String C_SECTION_MISSING = "SECTION_MISSING";
  public static final String C_SECTION_DUPLICATED = "SECTION_DUPLICATED";
  public static final String C_SECTION_UNEXPECTED = "SECTION_UNEXPECTED";
  public static final String C_SECTION_SHAPE = "SECTION_SHAPE";
  public static final String C_FIELD_UNKNOWN = "FIELD_UNKNOWN";
  public static final String C_FIELD_MISSING = "FIELD_MISSING";
  public static final String C_FIELD_TYPE = "FIELD_TYPE";
  public static final String C_FIELD_VALUE = "FIELD_VALUE";
  public static final String C_FIELD_EMPTY = "FIELD_EMPTY";
  public static final String C_DUPLICATE_INPUT_NAME = "DUPLICATE_INPUT_NAME";
  public static final String C_DUPLICATE_RULE_ID = "DUPLICATE_RULE_ID";
  public static final String C_DUPLICATE_ACTION_ID = "DUPLICATE_ACTION_ID";
  public static final String C_REF_INPUT_UNKNOWN = "REF_INPUT_UNKNOWN";
  public static final String C_REF_ACTION_UNKNOWN = "REF_ACTION_UNKNOWN";
  public static final String C_REF_CONDITION_OP = "REF_CONDITION_OP";
  public static final String C_REF_CONDITION_TYPE = "REF_CONDITION_TYPE";
  public static final String C_EMPTY_INPUTS = "EMPTY_INPUTS";
  public static final String C_EMPTY_RULES = "EMPTY_RULES";
  public static final String C_EMPTY_ACTIONS = "EMPTY_ACTIONS";
  public static final String C_REFUND_ACTION_MULTIPLE = "REFUND_ACTION_MULTIPLE";
  public static final String C_FIN_REFUND_MISSING = "FIN_REFUND_MISSING";
  public static final String C_FIN_REFUND_MAX_AMOUNT = "FIN_REFUND_MAX_AMOUNT";
  public static final String C_FIN_REFUND_AMOUNT_INPUT = "FIN_REFUND_AMOUNT_INPUT";
  public static final String C_FIN_ESCALATION_MISSING = "FIN_ESCALATION_MISSING";
  public static final String C_FIN_ESCALATION_BOUND = "FIN_ESCALATION_BOUND";
  public static final String C_QUESTION_REFUND_NOT_ALLOWED = "QUESTION_REFUND_NOT_ALLOWED";
  public static final String C_SOP_ID_MISMATCH = "SOP_ID_MISMATCH";

  // section names
  public static final String SEC_INTENT = "Intent (When to use)";
  public static final String SEC_DO_NOT = "Do Not Use When";
  public static final String SEC_INPUTS = "Inputs Required";
  public static final String SEC_RULES = "Eligibility Rules";
  public static final String SEC_ACTIONS = "Actions";
  public static final String SEC_BOUNDARIES = "Boundaries";
  public static final String SEC_MESSAGES = "Customer Messages";
}
