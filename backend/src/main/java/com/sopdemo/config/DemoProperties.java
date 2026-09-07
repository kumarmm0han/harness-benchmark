package com.sopdemo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo-only, labeled configuration. Identity values are fixed local demo identities,
 * NOT production authentication (FR-001, DES-012).
 */
@ConfigurationProperties(prefix = "sopdemo")
public record DemoProperties(
        Identities identities,
        Cors cors,
        Seed seed,
        SourceLimits source) {

    /** The two fixed demo identities (FR-001). */
    public record Identities(List<String> authors, List<String> consumers) {}

    /** CORS is limited to a single local UI origin (IR-001, DES-012). */
    public record Cors(String allowOrigin) {}

    /** Deterministic, idempotent seed (DR-003). Disabled by default; explicit demo profile. */
    public record Seed(boolean enabled) {}

    /** FR-020 source limits. */
    public record SourceLimits(int maxBytes, int maxYamlNesting) {}
}
