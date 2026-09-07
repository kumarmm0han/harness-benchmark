package com.sopdemo.domain.issue;

/**
 * Stable issue codes (PRN-005). Each code has a fixed {@link Stage}: structural issues are
 * always reported; semantic issues are reported only when structural checks found no
 * blocking problem (FR-034).
 */
public record IssueCode(String value, Stage stage) {
    // Parse / format (structural)
    public static final IssueCode INVALID_YAML = new IssueCode("INVALID_YAML", Stage.STRUCTURAL);
    public static final IssueCode DUPLICATE_KEY = new IssueCode("DUPLICATE_KEY", Stage.STRUCTURAL);
    public static final IssueCode ALIASES_REJECTED = new IssueCode("ALIASES_REJECTED", Stage.STRUCTURAL);
    public static final IssueCode CUSTOM_TAG_REJECTED = new IssueCode("CUSTOM_TAG_REJECTED", Stage.STRUCTURAL);
    public static final IssueCode NESTING_TOO_DEEP = new IssueCode("NESTING_TOO_DEEP", Stage.STRUCTURAL);
    public static final IssueCode NON_FINITE_NUMBER = new IssueCode("NON_FINITE_NUMBER", Stage.STRUCTURAL);
    public static final IssueCode UNKNOWN_SECTION = new IssueCode("UNKNOWN_SECTION", Stage.STRUCTURAL);
    public static final IssueCode MISSING_SECTION = new IssueCode("MISSING_SECTION", Stage.STRUCTURAL);
    public static final IssueCode DUPLICATE_SECTION = new IssueCode("DUPLICATE_SECTION", Stage.STRUCTURAL);
    public static final IssueCode PROSE_STRUCTURE_INVALID = new IssueCode("PROSE_STRUCTURE_INVALID", Stage.STRUCTURAL);
    public static final IssueCode FENCE_STRUCTURE_INVALID = new IssueCode("FENCE_STRUCTURE_INVALID", Stage.STRUCTURAL);
    public static final IssueCode SOURCE_TOO_LARGE = new IssueCode("SOURCE_TOO_LARGE", Stage.STRUCTURAL);

    // Shape / allowed-values (structural)
    public static final IssueCode MISSING_FIELD = new IssueCode("MISSING_FIELD", Stage.STRUCTURAL);
    public static final IssueCode UNKNOWN_KEY = new IssueCode("UNKNOWN_KEY", Stage.STRUCTURAL);
    public static final IssueCode UNSUPPORTED_TYPE = new IssueCode("UNSUPPORTED_TYPE", Stage.STRUCTURAL);
    public static final IssueCode EMPTY_TEXT = new IssueCode("EMPTY_TEXT", Stage.STRUCTURAL);
    public static final IssueCode MALFORMED_ID = new IssueCode("MALFORMED_ID", Stage.STRUCTURAL);
    public static final IssueCode ENUM_INVALID = new IssueCode("ENUM_INVALID", Stage.STRUCTURAL);
    public static final IssueCode DOMAIN_FOR_INTENT = new IssueCode("DOMAIN_FOR_INTENT", Stage.STRUCTURAL);
    public static final IssueCode MISSING_ITEM = new IssueCode("MISSING_ITEM", Stage.STRUCTURAL);
    public static final IssueCode DUPLICATE_NAME = new IssueCode("DUPLICATE_NAME", Stage.STRUCTURAL);
    public static final IssueCode DUPLICATE_ID = new IssueCode("DUPLICATE_ID", Stage.STRUCTURAL);

    // Cross-references (semantic)
    public static final IssueCode UNKNOWN_INPUT_REFERENCE = new IssueCode("UNKNOWN_INPUT_REFERENCE", Stage.SEMANTIC);
    public static final IssueCode UNKNOWN_ACTION_REFERENCE = new IssueCode("UNKNOWN_ACTION_REFERENCE", Stage.SEMANTIC);
    public static final IssueCode OP_VALUE_TYPE_MISMATCH = new IssueCode("OP_VALUE_TYPE_MISMATCH", Stage.SEMANTIC);
    public static final IssueCode BOOL_OP_RESTRICTION = new IssueCode("BOOL_OP_RESTRICTION", Stage.SEMANTIC);
    public static final IssueCode INVALID_OP = new IssueCode("INVALID_OP", Stage.SEMANTIC);

    // Financial safety (semantic) — FR-032
    public static final IssueCode FINANCIAL_MISSING_LIMIT = new IssueCode("FINANCIAL_MISSING_LIMIT", Stage.SEMANTIC);
    public static final IssueCode FINANCIAL_MISSING_ESCALATION = new IssueCode("FINANCIAL_MISSING_ESCALATION", Stage.SEMANTIC);
    public static final IssueCode FINANCIAL_AMOUNT_MISMATCH = new IssueCode("FINANCIAL_AMOUNT_MISMATCH", Stage.SEMANTIC);
    public static final IssueCode MULTIPLE_REFUND_ACTIONS = new IssueCode("MULTIPLE_REFUND_ACTIONS", Stage.SEMANTIC);
    public static final IssueCode MISSING_REFUND_AMOUNT_INPUT = new IssueCode("MISSING_REFUND_AMOUNT_INPUT", Stage.SEMANTIC);
    public static final IssueCode ANSWER_QUESTION_REFUND = new IssueCode("ANSWER_QUESTION_REFUND", Stage.SEMANTIC);
}
