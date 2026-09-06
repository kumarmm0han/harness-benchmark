package com.sop.api;

import org.springframework.http.HttpStatus;

/**
 * A stable error code (DES-001, IR-001) with its canonical HTTP status.
 * Used by every exception in this API and by {@link GlobalExceptionHandler}.
 */
public enum ErrorCode {
  MALFORMED(HttpStatus.BAD_REQUEST),
  INVALID_FILTER(HttpStatus.BAD_REQUEST),
  MISSING_IDENTITY(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  STALE_REVISION(HttpStatus.CONFLICT),
  PUBLICATION_CONFLICT(HttpStatus.CONFLICT),
  SOURCE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
  VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
  INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

  public final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) { this.httpStatus = httpStatus; }
}
