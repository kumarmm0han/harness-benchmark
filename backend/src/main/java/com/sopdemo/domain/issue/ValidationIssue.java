package com.sopdemo.domain.issue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;

/**
 * A single deterministic validation issue (FR-034, PRN-005). Ordered stably by source
 * path then code, so the response is fully deterministic for identical input (PRN-005).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationIssue(String code, String stage, String message, String path) {

    public static final Comparator<ValidationIssue> STABLE_ORDER =
            Comparator.comparing(ValidationIssue::path, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(ValidationIssue::code)
                    .thenComparing(ValidationIssue::message, Comparator.nullsFirst(String::compareTo));

    public static ValidationIssue of(IssueCode code, String message, String path) {
        return new ValidationIssue(code.value(), code.stage().toString(), message, path);
    }
}
