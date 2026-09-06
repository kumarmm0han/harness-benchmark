package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DraftRequest(@JsonProperty("source") String source) {}
