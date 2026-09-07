package com.sopdemo.identity;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sopdemo.api.ApiExceptionHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves the demo identity on every API request (PRN-004 backend authority):
 * missing or unknown {@code X-Demo-User} → 401 (FR-001, IR-001).
 * The resolved principal is stored as a request attribute for controllers.
 */
@Component
@Order(1)
public class IdentityFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Demo-User";
    public static final String ATTRIBUTE = "sop.identity";

    private final String authorIdentity;
    private final String consumerIdentity;

    public IdentityFilter(
            @Value("${sop.author-identity}") String authorIdentity,
            @Value("${sop.consumer-identity}") String consumerIdentity) {
        this.authorIdentity = authorIdentity;
        this.consumerIdentity = consumerIdentity;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        // Browser CORS preflight is a handshake, not an authenticated user action; let Spring CORS answer it first.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        Identity identity = resolve(header);
        if (identity == null) {
            ApiExceptionHandler.writeError(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                    "A valid demo identity is required (X-Demo-User header).");
            return;
        }
        request.setAttribute(ATTRIBUTE, identity);
        chain.doFilter(request, response);
    }

    private Identity resolve(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.equals(authorIdentity)) {
            return new Identity(header, Identity.Role.AUTHOR);
        }
        if (header.equals(consumerIdentity)) {
            return new Identity(header, Identity.Role.CONSUMER);
        }
        return null;
    }
}
