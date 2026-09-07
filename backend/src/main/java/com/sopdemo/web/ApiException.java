package com.sopdemo.web;

import com.sopdemo.domain.issue.ValidationIssue;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Application-level API error (IR-001): carries an HTTP status, a stable {@code code},
 * a human-readable {@code message}, and an optional {@code issues} list. No SQL, stack, or
 * internal detail is exposed (PRN-004, NFR-020).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<ValidationIssue> issues;

    public ApiException(int status, String code, String message, List<ValidationIssue> issues) {
        super(message);
        this.status = status;
        this.code = code;
        this.issues = issues == null ? List.of() : issues;
    }

    public static ApiException unauthorized(String msg) {
        return new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", msg, null);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", msg, null);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", msg, null);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(HttpServletResponse.SC_CONFLICT, "CONFLICT", msg, null);
    }

    public static ApiException oversized(String msg) {
        return new ApiException(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "SOURCE_TOO_LARGE", msg, null);
    }

    public static ApiException unprocessable(String msg, List<ValidationIssue> issues) {
        return new ApiException(422, "UNPROCESSABLE", msg, issues);
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", msg, null);
    }

    public static ApiException internal(String msg) {
        return new ApiException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL", msg, null);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ValidationIssue> issues() {
        return issues;
    }
}
