package com.sopdemo.content;

import java.nio.charset.StandardCharsets;

/**
 * Hard source-size gate: 65,536 UTF-8 bytes (FR-020). Enforced before parsing
 * so oversized input never reaches the YAML parser (PRN-004).
 */
public final class SourceLimits {

    public static final int MAX_SOURCE_BYTES = 65_536;

    private SourceLimits() {
    }

    public static void check(String source, int maxBytes) {
        int actual = source.getBytes(StandardCharsets.UTF_8).length;
        if (actual > maxBytes) {
            throw new SourceTooLargeException(maxBytes, actual);
        }
    }
}
