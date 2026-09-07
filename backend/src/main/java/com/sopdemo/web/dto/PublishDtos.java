package com.sopdemo.web.dto;

/** Request DTOs for author-only validate/publish (IR-001). */
public final class PublishDtos {
    private PublishDtos() {}

    /** {@code POST /validate} body: the full authored source. */
    public record ValidateRequest(String source) {}

    /** {@code POST /sops/{sop_id}/publish} body: the exact saved draft revision to publish. */
    public record PublishRequest(Long revision) {}
}
