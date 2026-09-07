package com.sopdemo.api;

import java.util.List;
import java.util.Map;

/**
 * Normalized API error shape required by IR-001:
 * {@code {code, message, issues}} with {@code issues: []} when no field issues apply.
 */
public record ApiError(String code, String message, List<Map<String, Object>> issues) {

    public ApiError {
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
