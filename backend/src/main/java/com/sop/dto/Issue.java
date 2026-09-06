package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single validation issue (FR-034). Returned in a stable (path, code) order.
 * `stage` is "structural" or "semantic" (FR-034 / DES-001).
 * `path` may be null for request-level issues (e.g. SOURCE_TOO_LARGE).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Issue(String code, String stage, String message, String path) {

  public static Issue structural(String code, String message, String path) {
    return new Issue(code, "structural", message, normalizePath(path));
  }

  public static Issue semantic(String code, String message, String path) {
    return new Issue(code, "semantic", message, normalizePath(path));
  }

  private static String normalizePath(String path) {
    return (path == null || path.isEmpty()) ? null : path;
  }
}
