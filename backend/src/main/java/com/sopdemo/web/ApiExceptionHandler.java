package com.sopdemo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps controller-scope exceptions to the fixed envelope with the correct HTTP status
 * (IR-001, PRN-005, NFR-020). The status and body come from {@link ApiErrors}; filter-scope
 * identity failures write the same envelope directly (see {@code IdentityFilter}).
 * Unexpected failures → generic 500 with no internals (logged server-side only).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiErrors errors;

    public ApiExceptionHandler(ApiErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrors.Body> handleApi(ApiException ex) {
        log.debug("api error {} {}", ex.status(), ex.code());
        return errors.response(ex.status(), ex.code(), ex.getMessage(), ex.issues());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrors.Body> handleUnreadable() {
        return errors.response(400, "BAD_REQUEST", "malformed JSON body; expected an object with a `source` string", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrors.Body> handleTypeMismatch() {
        return errors.response(400, "BAD_REQUEST", "invalid request parameter", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrors.Body> fallback(Exception ex) {
        log.warn("unhandled exception during request", ex);
        return errors.response(500, "INTERNAL", "internal error", null);
    }
}
