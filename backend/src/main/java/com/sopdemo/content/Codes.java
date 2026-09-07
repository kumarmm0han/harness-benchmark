package com.sopdemo.content;

/**
 * Centralized, stable validation issue codes (PRN-005, FR-034).
 * One place, stable strings; messages are defined where they are raised.
 */
public final class Codes {

    private Codes() {
    }

    public static final String SOURCE_TOO_LARGE = "source-too-large";
    public static final String MALFORMED_YAML = "malformed-yaml";
    public static final String DUPLICATE_KEY = "duplicate-key";
    public static final String ALIAS_OR_TAG = "alias-or-tag";
    public static final String NESTING_TOO_DEEP = "nesting-too-deep";
    public static final String NON_FINITE_NUMBER = "non-finite-number";

    public static final String MISSING_FRONTMATTER = "missing-frontmatter";
    public static final String FRONTMATTER_NOT_MAPPING = "frontmatter-not-mapping";
    public static final String UNKNOWN_SECTION = "unknown-section";
    public static final String MISSING_SECTION = "missing-section";
    public static final String SECTION_ORDER = "section-order";
    public static final String INVALID_BULLET_SECTION = "invalid-bullet-section";
    public static final String EMPTY_SECTION = "empty-section";
    public static final String INVALID_MACHINE_SECTION = "invalid-machine-section";
    public static final String WRONG_SECTION_TYPE = "wrong-section-type";

    public static final String MISSING_FIELD = "missing-field";
    public static final String UNKNOWN_FIELD = "unknown-field";
    public static final String INVALID_ENUM = "invalid-enum";
    public static final String INVALID_ID = "invalid-id";
    public static final String INVALID_TYPE = "invalid-type";
    public static final String EMPTY_REQUIRED_TEXT = "empty-required-text";

    public static final String EMPTY_REQUIRED_LIST = "empty-required-list";
    public static final String DUPLICATE_ID = "duplicate-id";
    public static final String BAD_REFERENCE = "bad-reference";
    public static final String TYPE_MISMATCH = "type-mismatch";
    public static final String OPERATOR_MISMATCH = "operator-mismatch";

    public static final String REFUND_LIMIT_MISSING = "refund-limit-missing";
    public static final String REFUND_ESCALATION_MISSING = "refund-escalation-missing";
    public static final String REFUND_NOT_ALLOWED = "refund-not-allowed";
    public static final String REFUND_INTENT_REQUIRES_BILLING = "refund-intent-requires-billing";

    public static final String SOP_ID_MISMATCH = "sop-id-mismatch";
}
