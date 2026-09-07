package com.sopdemo.domain.parse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seven supported level-two sections (spec.md §1). Exactly one of each is required;
 * order is not mandated (recorded assumption). `shape` is the expected YAML kind for
 * machine sections; prose sections carry plain bullet lines.
 */
public enum SectionKind {
    USE_WHEN("Intent (When to use)", Shape.PROSE),
    DO_NOT_USE_WHEN("Do Not Use When", Shape.PROSE),
    INPUTS("Inputs Required", Shape.LIST),
    RULES("Eligibility Rules", Shape.LIST),
    ACTIONS("Actions", Shape.LIST),
    BOUNDARIES("Boundaries", Shape.MAP),
    CUSTOMER_MESSAGES("Customer Messages", Shape.MAP);

    public enum Shape { PROSE, LIST, MAP }

    private final String title;
    private final Shape shape;

    SectionKind(String title, Shape shape) {
        this.title = title;
        this.shape = shape;
    }

    public String title() {
        return title;
    }

    public Shape shape() {
        return shape;
    }

    /** All section titles keyed for fast lookup (title -> kind). */
    public static final Map<String, SectionKind> BY_TITLE = buildByTitle();

    private static Map<String, SectionKind> buildByTitle() {
        Map<String, SectionKind> m = new LinkedHashMap<>();
        for (SectionKind k : values()) {
            m.put(k.title, k);
        }
        return Map.copyOf(m);
    }
}
