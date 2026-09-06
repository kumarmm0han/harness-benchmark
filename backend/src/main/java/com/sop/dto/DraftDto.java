package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DraftDto(
    String sop_id,
    long revision,
    String source,
    boolean publish_failed,
    Instant updated_at
) {
  public DraftDto(String sopId, long revision, String source, boolean publishFailed) {
    this(sopId, revision, source, publishFailed, null);
  }
}
