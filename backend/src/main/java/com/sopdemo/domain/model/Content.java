package com.sopdemo.domain.model;

import java.util.List;

/**
 * The canonical content shape from spec.md §4 (FR-021, PRN-001). Serialized with the
 * global SNAKE_CASE naming strategy to match the documented JSON exactly. `non_null`
 * inclusion omits absent optional fields (e.g. a non-{@code refund} action's maxAmount).
 */
public record Content(
        String sopId,
        String title,
        String ownerTeam,
        String domain,
        String intent,
        String riskLevel,
        String maxAutonomy,
        Policy policy,
        List<InputDecl> inputs,
        List<Rule> rules,
        List<Action> actions,
        Boundaries boundaries,
        CustomerMessages customerMessages) {}
