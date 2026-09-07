package com.sopdemo.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A controlled, expected API failure. The exception carries the HTTP status, a stable
 * machine-readable {@code code}, a human-readable message, and optionally validation
 * issues. Messages never include SQL, stack traces, or parser internals (NFR-020).
 */
public class SopException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<Map<String, Object>> issues;

    public SopException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public SopException(HttpStatus status, String code, String message, List<Map<String, Object>> issues) {
        super(message);
        this.status = status;
        this.code = code;
        this.issues = List.copyOf(issues);
    }

    public ApiError toApiError() {
        return new ApiError(code, getMessage(), issues);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<Map<String, Object>> issues() {
        return issues;
    }
}
