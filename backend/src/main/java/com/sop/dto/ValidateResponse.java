package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response of POST /validate (IR-001). `content` is null when `valid` is false
 * (spec §4: "Invalid content yields content: null").
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ValidateResponse(
    boolean valid,
    List<Issue> issues,
    com.sop.domain.Content content
) {
  public static ValidateResponse invalid(List<Issue> issues) {
    return new ValidateResponse(false, issues, null);
  }

  public static ValidateResponse valid(com.sop.domain.Content content) {
    return new ValidateResponse(true, List.of(), content);
  }
}
