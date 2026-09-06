package com.sop.domain;

/** Thrown by the safe-YAML loader when any restricted construct is encountered.
 *  Carries the issue code (from DES-001) and a short human message. */
public class SafeYamlException extends RuntimeException {
  private final String code;
  public SafeYamlException(String code, String message) {
    super(message);
    this.code = code;
  }
  public String code() { return code; }

  // Common codes (see DES-001 in TECHNICAL_DESIGN)
  public static final String PARSE_FAILED = "PARSE_FAILED";
  public static final String SOURCE_TOO_LARGE = "SOURCE_TOO_LARGE";
  public static final String ALIAS = "ALIAS";
  public static final String NESTING_TOO_DEEP = "NESTING_TOO_DEEP";
  public static final String NON_FINITE_NUMBER = "NON_FINITE_NUMBER";
  public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
  public static final String UNKNOWN_TAG = "UNKNOWN_TAG";
  public static final String EMPTY = "EMPTY";
}
