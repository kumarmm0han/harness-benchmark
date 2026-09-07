package com.sopdemo.domain.parse;

import com.sopdemo.domain.issue.IssueCode;

/** Carries a safe-parse failure with a specific reason code (FR-020, NFR-020). */
public class YamlFailure extends RuntimeException {
    private final IssueCode code;

    public YamlFailure(IssueCode code, String message) {
        super(message);
        this.code = code;
    }

    public IssueCode code() {
        return code;
    }
}
