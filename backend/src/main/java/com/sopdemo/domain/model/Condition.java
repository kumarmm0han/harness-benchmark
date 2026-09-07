package com.sopdemo.domain.model;

/**
 * A rule condition. `value` is a finite Number for numeric inputs or a Boolean for
 * boolean inputs (spec.md §2, §4). Kept as Object to preserve type fidelity.
 */
public record Condition(String input, String op, Object value) {}
