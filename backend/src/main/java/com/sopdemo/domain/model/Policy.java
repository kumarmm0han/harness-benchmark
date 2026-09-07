package com.sopdemo.domain.model;

import java.util.List;

/** `policy` object: prose sections compiled to ordered string lists (spec.md §1/§4). */
public record Policy(List<String> useWhen, List<String> doNotUseWhen) {}
