package com.sopdemo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness/readiness endpoint used by Docker Compose health checks. Not part of /api/v1. */
@RestController
public class HealthController {
    @GetMapping("/healthz")
    public Record health() {
        return new Record("UP");
    }

    public record Record(String status) {}
}
