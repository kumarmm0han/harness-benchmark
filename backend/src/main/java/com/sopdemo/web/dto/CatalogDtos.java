package com.sopdemo.web.dto;

/** {@code GET /sops} list item: the required identity summary (FR-050). */
public final class CatalogDtos {
    private CatalogDtos() {}

    public record SopSummary(String sopId, String title, int version, String domain, String risk) {}
}
