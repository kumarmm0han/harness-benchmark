package com.sopdemo.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sopdemo.identity.Authorize;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Test-only probe endpoints to exercise the identity/authorization/auth-error pipeline
 * (TASK-003) without waiting for the real resource controllers (TASK-007…009).
 * Lives in test sources only; never part of the shipped API (IR-001 keeps the contract exact).
 */
@RestController
public class TestProbeController {

    @GetMapping("/api/v1/probe/open")
    public Map<String, Object> open() {
        return Map.of("ok", true);
    }

    @GetMapping("/api/v1/probe/author")
    public Map<String, Object> author(HttpServletRequest request) {
        Authorize.requireAuthor(request);
        return Map.of("ok", true, "who", Authorize.current(request).name());
    }

    @GetMapping("/api/v1/probe/error")
    public Map<String, Object> error() {
        throw new IllegalStateException("secret-internal-pg-password-xyz");
    }
}
