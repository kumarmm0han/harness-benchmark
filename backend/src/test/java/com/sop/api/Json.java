package com.sop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small JSON assertions helper for the integration tests. */
public final class Json {

  private static final ObjectMapper M = new ObjectMapper();
  private final JsonNode root;

  private Json(JsonNode root) { this.root = root; }

  public static Json of(String body) {
    try {
      return new Json(M.readTree(body == null ? "null" : body));
    } catch (Exception e) {
      throw new IllegalStateException("invalid JSON body: " + body, e);
    }
  }

  public JsonNode root() { return root; }

  public String text(String field) {
    return get(field).asText(null);
  }

  public boolean bool(String field, boolean dflt) {
    return get(field).asBoolean(dflt);
  }

  public int intv(String field, int dflt) {
    return get(field).asInt(dflt);
  }

  public Json sub(String field) { return new Json(get(field)); }

  private JsonNode get(String f) {
    JsonNode n = root.get(f);
    return (n == null) ? M.createObjectNode() : n;
  }
}
