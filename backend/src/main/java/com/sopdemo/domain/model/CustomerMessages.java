package com.sopdemo.domain.model;

/** `customer_messages` object: two nonempty policy strings (spec.md §2). */
public record CustomerMessages(String primary, String escalation) {}
