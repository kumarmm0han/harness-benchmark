package com.sopdemo.content;

/**
 * A single validation issue (FR-034): {@code {code, stage, message, path}}.
 * {@code stage} is {@code structural} or {@code semantic}. Messages are stable,
 * readable, and never leak parser internals (PRN-005, NFR-020).
 */
public record Issue(String code, String stage, String message, String path) {

    public static final String STAGE_STRUCTURAL = "structural";
    public static final String STAGE_SEMANTIC = "semantic";

    public static Issue structural(String code, String message, String path) {
        return new Issue(code, STAGE_STRUCTURAL, message, path);
    }

    public static Issue semantic(String code, String message, String path) {
        return new Issue(code, STAGE_SEMANTIC, message, path);
    }
}
