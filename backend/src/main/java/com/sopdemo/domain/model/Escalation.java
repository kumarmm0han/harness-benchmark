package com.sopdemo.domain.model;

/**
 * An escalation boundary (spec.md §2). `amount` is a positive finite number. All fields
 * are required; this record describes declared policy and is never executed (NFR-020).
 */
public record Escalation(String actionId, String input, String op, double amount, String targetActionId) {}
