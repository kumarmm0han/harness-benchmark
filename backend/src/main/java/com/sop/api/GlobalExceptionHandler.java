package com.sop.api;

import com.sop.dto.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps exceptions to the unified error envelope (DES-009 / IR-001 / NFR-020).
 * 5xx responses are generic — no SQL, stack traces, or internals (PRN-004).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorBody> api(ApiException e) {
    return ResponseEntity
        .status(e.code().httpStatus)
        .body(new ErrorBody(e.code().name(), e.getMessage(), e.issues()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException e) {
    log.warn("data integrity violation: {}", e.getMostSpecificCause().getMessage());
    // The unique (sop_id, draft_revision) constraint firing means the same
    // revision was already published (DES-008b).
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorBody(ErrorCode.PUBLICATION_CONFLICT.name(),
            "this draft revision has already been published", List.of()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorBody> unexpected(Exception e) {
    log.error("unexpected error", e);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorBody(ErrorCode.INTERNAL.name(), "An unexpected error occurred.", List.of()));
  }
}
