package com.sop.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A single published SOP — the envelope (spec §4) wrapping the canonical content. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Envelope(
    @JsonProperty("sop_id") String sopId,
    @JsonProperty("version") Integer version,
    @JsonProperty("published_at") String publishedAt,
    @JsonProperty("content") Content content
) {}
