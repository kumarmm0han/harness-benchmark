package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SopSummary(
    @JsonProperty("sop_id") String sopId,
    @JsonProperty("title") String title,
    @JsonProperty("version") Integer version,
    @JsonProperty("domain") String domain,
    @JsonProperty("risk") String risk
) {}
