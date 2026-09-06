package com.sop.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sop.dto.Issue;

import java.util.List;

/** Unified error envelope (DES-009 / IR-001). `issues` is always a list; empty when N/A. */
public record ErrorBody(
    @JsonProperty("code") String code,
    @JsonProperty("message") String message,
    @JsonProperty("issues") List<Issue> issues
) {
  public static ErrorBody of(ErrorCode code, String message) {
    return new ErrorBody(code.name(), message == null ? "" : message, List.of());
  }
}
