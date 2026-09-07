package com.sopdemo.api;

/**
 * Malformed request envelope (IR-001): mapped to HTTP 400 with code {@code invalid-request}.
 * Distinct from rejected *content* (which is a 200 with issues, FR-034).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
