package com.sopdemo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sopdemo.domain.issue.ValidationIssue;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the fixed error envelope {@code {code, message, issues}}
 * (IR-001, PRN-005, PRN-003). Both the controller-level advice and the identity filter use
 * this, so the shape and status mapping are produced in exactly one place and never
 * diverge. Unexpected failures carry a generic body with no SQL, stack, or internal detail
 * (PRN-004, NFR-020).
 */
@Component
public class ApiErrors {

    /** The fixed API error body (IR-001). */
    public record Body(String code, String message, List<ValidationIssue> issues) {
        public Body(String code, String message) {
            this(code, message, List.of());
        }
    }

    private final ObjectMapper mapper;

    public ApiErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static List<ValidationIssue> orEmpty(List<ValidationIssue> issues) {
        return issues == null ? List.of() : issues;
    }

    /** Controller-scope: carry the HTTP status alongside the serialized body. */
    public ResponseEntity<Body> response(int status, String code, String message, List<ValidationIssue> issues) {
        return new ResponseEntity<>(new Body(code, message, orEmpty(issues)), HttpStatus.valueOf(status));
    }

    /** Filter-scope: write the status and body directly to the servlet response. */
    public void writeHttp(
            HttpServletResponse res, int status, String code, String message, List<ValidationIssue> issues) {
        try {
            res.setStatus(status);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(res.getWriter(), new Body(code, message, orEmpty(issues)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write the API error envelope", e);
        }
    }
}
