package com.sop.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Canonical content shape (spec §4). Field names match the spec exactly via @JsonProperty. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Content(
    @JsonProperty("sop_id") String sopId,
    @JsonProperty("title") String title,
    @JsonProperty("owner_team") String ownerTeam,
    @JsonProperty("domain") String domain,
    @JsonProperty("intent") String intent,
    @JsonProperty("risk_level") String riskLevel,
    @JsonProperty("max_autonomy") String maxAutonomy,
    @JsonProperty("policy") Policy policy,
    @JsonProperty("inputs") java.util.List<Input> inputs,
    @JsonProperty("rules") java.util.List<Rule> rules,
    @JsonProperty("actions") java.util.List<Action> actions,
    @JsonProperty("boundaries") Boundaries boundaries,
    @JsonProperty("customer_messages") Messages customerMessages
) {
  public record Policy(
      @JsonProperty("use_when") java.util.List<String> useWhen,
      @JsonProperty("do_not_use_when") java.util.List<String> doNotUseWhen) {}

  public record Input(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type) {}

  public record Condition(
      @JsonProperty("input") String input,
      @JsonProperty("op") String op,
      @JsonProperty("value") Object value) {}

  public record Rule(
      @JsonProperty("id") String id,
      @JsonProperty("conditions") java.util.List<Condition> conditions,
      @JsonProperty("action_ids") java.util.List<String> actionIds) {}

  public record Action(
      @JsonProperty("id") String id,
      @JsonProperty("kind") String kind,
      @JsonProperty("description") String description,
      @JsonProperty("max_amount") Object maxAmount) {}

  public record Escalation(
      @JsonProperty("action_id") String actionId,
      @JsonProperty("input") String input,
      @JsonProperty("op") String op,
      @JsonProperty("amount") Object amount,
      @JsonProperty("target_action_id") String targetActionId) {}

  public record Boundaries(@JsonProperty("escalation") java.util.List<Escalation> escalation) {}

  public record Messages(
      @JsonProperty("primary") String primary,
      @JsonProperty("escalation") String escalation) {}
}
