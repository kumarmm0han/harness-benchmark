package com.sopdemo.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed, unresolved document between parsing and validation (DES-104). */
public record ParsedDoc(
        Map<String, Object> frontMatter, // null if structurally unusable
        List<String> useWhen,
        List<String> doNotUseWhen,
        Object inputs, // expected: List
        Object rules, // expected: List
        Object actions, // expected: List
        Object boundaries, // expected: Map
        Object customerMessages, // expected: Map
        List<String> headingOrder, // H2 headings in document order
        List<Issue> issues) {

    public boolean structurallyUsable() {
        return frontMatter != null && issues.isEmpty();
    }
}

