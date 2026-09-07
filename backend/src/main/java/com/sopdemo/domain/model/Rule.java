package com.sopdemo.domain.model;

import java.util.List;

/** `rules[]` entry: id, conditions conjoined, and referenced action ids (spec.md §2). */
public record Rule(String id, List<Condition> conditions, List<String> actionIds) {}
