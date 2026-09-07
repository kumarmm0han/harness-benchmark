package com.sopdemo.domain.model;

/**
 * `actions[]` entry. `maxAmount` is a positive finite double required for {@code refund}
 * and disallowed for other kinds (spec.md §2); null when absent and therefore omitted.
 */
public record Action(String id, String kind, String description, Double maxAmount) {}
