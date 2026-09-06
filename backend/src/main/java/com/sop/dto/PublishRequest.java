package com.sop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublishRequest(@JsonProperty("revision") Long revision) {}
