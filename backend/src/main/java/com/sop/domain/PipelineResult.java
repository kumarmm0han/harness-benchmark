package com.sop.domain;

import com.sop.dto.Issue;
import java.util.List;

/** Result of the validation pipeline (DES-006). */
public record PipelineResult(
    boolean valid,
    List<Issue> issues,
    Content content,   // null when invalid; built by caller on success
    ParsedDocument parsedDocument  // for the caller to compute content
) {}
