package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DraftSummary(
    String sop_id,
    long revision,
    boolean publish_failed
) {}
