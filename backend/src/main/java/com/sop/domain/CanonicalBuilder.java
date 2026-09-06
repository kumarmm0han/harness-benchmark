package com.sop.domain;

import com.sop.dto.Issue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a validated ParsedDocument into the canonical Content (DES-006 / spec §4).
 * Preserves YAML values without implicit conversions. Runs only when the document
 * is valid — callers gate on issues.isEmpty().
 */
public final class CanonicalBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final MapperConfig CONFIG = new MapperConfig();

  private CanonicalBuilder() {}

  public static Content build(ParsedDocument doc) {
    Map<String, Object> fm = doc.frontMatter;

    // Front matter → flat strings
    String sopId = asString(fm.get("sop_id"));
    String title = asString(fm.get("title"));
    String ownerTeam = asString(fm.get("owner_team"));
    String domain = asString(fm.get("domain"));
    String intent = asString(fm.get("intent"));
    String riskLevel = asString(fm.get("risk_level"));
    String maxAutonomy = asString(fm.get("max_autonomy"));

    // Policy
    List<String> useWhen = doc.split != null
        ? doc.split.proseSections.getOrDefault(Contract.SEC_INTENT, List.of())
        : List.of();
    List<String> doNotUseWhen = doc.split != null
        ? doc.split.proseSections.getOrDefault(Contract.SEC_DO_NOT, List.of())
        : List.of();
    Content.Policy policy = new Content.Policy(useWhen, doNotUseWhen);

    Map<String, Object> mv = doc.machineValues;

    List<Content.Input> inputs = toInputs(mv.get(Contract.SEC_INPUTS));
    List<Content.Rule> rules = toRules(mv.get(Contract.SEC_RULES));
    List<Content.Action> actions = toActions(mv.get(Contract.SEC_ACTIONS));
    Content.Boundaries boundaries = toBoundaries(mv.get(Contract.SEC_BOUNDARIES));
    Content.Messages messages = toMessages(mv.get(Contract.SEC_MESSAGES));

    return new Content(
        sopId, title, ownerTeam, domain, intent, riskLevel, maxAutonomy,
        policy, inputs, rules, actions, boundaries, messages);
  }

  private static List<Content.Input> toInputs(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Content.Input> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) continue;
      out.add(new Content.Input(
          asString(m.get("name")),
          asString(m.get("type"))));
    }
    return out;
  }

  private static List<Content.Rule> toRules(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Content.Rule> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) continue;
      List<Content.Condition> conds = new ArrayList<>();
      if (m.get("conditions") instanceof List<?> cl) {
        for (Object c : cl) {
          if (!(c instanceof Map<?, ?> cm)) continue;
          conds.add(new Content.Condition(
              asString(cm.get("input")),
              asString(cm.get("op")),
              cm.get("value") == null ? null : cm.get("value")));
        }
      }
      List<String> actionIds = new ArrayList<>();
      if (m.get("action_ids") instanceof List<?> al) {
        for (Object a : al) if (a instanceof String s) actionIds.add(s);
      }
      out.add(new Content.Rule(
          asString(m.get("id")), conds, actionIds));
    }
    return out;
  }

  private static List<Content.Action> toActions(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Content.Action> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) continue;
      out.add(new Content.Action(
          asString(m.get("id")),
          asString(m.get("kind")),
          asString(m.get("description")),
          m.get("max_amount") == null ? null : m.get("max_amount")));
    }
    return out;
  }

  private static Content.Boundaries toBoundaries(Object value) {
    if (!(value instanceof Map<?, ?> m)) return new Content.Boundaries(List.of());
    Object esc = m.get("escalation");
    if (!(esc instanceof List<?> list)) return new Content.Boundaries(List.of());
    List<Content.Escalation> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> em)) continue;
      out.add(new Content.Escalation(
          asString(em.get("action_id")),
          asString(em.get("input")),
          asString(em.get("op")),
          em.get("amount") == null ? null : em.get("amount"),
          asString(em.get("target_action_id"))));
    }
    return new Content.Boundaries(out);
  }

  private static Content.Messages toMessages(Object value) {
    if (!(value instanceof Map<?, ?> m)) return new Content.Messages(null, null);
    return new Content.Messages(asString(m.get("primary")), asString(m.get("escalation")));
  }

  private static String asString(Object v) {
    return v instanceof String s ? s : null;
  }

  private record MapperConfig() {}
}
