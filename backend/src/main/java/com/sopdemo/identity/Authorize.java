package com.sopdemo.identity;

import org.springframework.http.HttpStatus;

import com.sopdemo.api.SopException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Server-side authorization checks (PRN-004): the UI cannot replace these.
 * A consumer attempting an author operation receives 403 (FR-001, IR-001).
 */
public final class Authorize {

    private Authorize() {
    }

    public static Identity current(HttpServletRequest request) {
        Object attr = request.getAttribute(IdentityFilter.ATTRIBUTE);
        if (attr instanceof Identity identity) {
            return identity;
        }
        // Outside the API filter chain (should not happen for /api/**).
        throw new SopException(HttpStatus.UNAUTHORIZED, "unauthorized", "No demo identity present.");
    }

    public static void requireAuthor(HttpServletRequest request) {
        Identity identity = current(request);
        if (!identity.isAuthor()) {
            throw new SopException(HttpStatus.FORBIDDEN, "forbidden",
                    "This operation requires the demo author identity.");
        }
    }
}
