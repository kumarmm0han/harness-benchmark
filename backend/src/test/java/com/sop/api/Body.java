package com.sop.api;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Builds JSON request bodies for the integration tests (via Jackson). */
public final class Body {
  private Body() {}

  private static final ObjectMapper M = new ObjectMapper();

  /** {"source": md} -- body for PUT /drafts/{id} and POST /validate */
  public static String draft(String md) {
    try {
      java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
      m.put("source", md);
      return M.writeValueAsString(m);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** {"revision": N} -- body for POST /sops/{id}/publish */
  public static String revision(long n) {
    try {
      java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
      m.put("revision", n);
      return M.writeValueAsString(m);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
