package com.sopdemo.domain.model;

import java.util.List;

/** `boundaries` object: an escalation list (may be empty for non-financial SOPs). */
public record Boundaries(List<Escalation> escalation) {}
