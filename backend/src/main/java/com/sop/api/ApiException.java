package com.sop.api;

import com.sop.dto.Issue;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final ErrorCode code;
  private final String message;
  private final java.util.List<Issue> issues;

  public ApiException(ErrorCode code, String message) {
    this(code, message, java.util.List.of());
  }

  public ApiException(ErrorCode code, String message, java.util.List<Issue> issues) {
    super(message);
    this.code = code;
    this.message = message;
    this.issues = issues == null ? java.util.List.of() : issues;
  }

  public ErrorCode code() { return code; }

  @Override
  public String getMessage() { return message; }

  public java.util.List<Issue> issues() { return issues; }

  public HttpStatus httpStatus() { return code.httpStatus; }
}
