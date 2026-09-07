package com.sopdemo.domain;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Content;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The canonical validate/publication result (FR-034): `{valid, issues, content}` with
 * `content` null when invalid. `issues` is ordered stably (path then code), never empty
 * in shape (may be an empty list) — FR-034/PRN-005.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PolicyResult(boolean valid, List<ValidationIssue> issues, Content content) {}
