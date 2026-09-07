package com.sopdemo.domain.compile;

import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_KEY;
import static com.sopdemo.domain.issue.IssueCode.UNSUPPORTED_TYPE;

import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Action;
import com.sopdemo.domain.model.Boundaries;
import com.sopdemo.domain.model.Condition;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.model.CustomerMessages;
import com.sopdemo.domain.model.Escalation;
import com.sopdemo.domain.model.InputDecl;
import com.sopdemo.domain.model.Policy;
import com.sopdemo.domain.model.Rule;
import com.sopdemo.domain.parse.ParsedSection;
import com.sopdemo.domain.parse.ParsedSource;
import com.sopdemo.domain.parse.SectionKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical compilation (DES-003, FR-021). Maps the neutral parse result into the exact
 * {@link Content} shape from spec.md §4, enforcing JSON kind (list/map/scalar) and the
 * allowed key set per supported structure (rejecting unknown keys, PRN-001/PRN-005). Runs
 * only when parsing produced no issues (so all sections are present). Value-level business
 * validation (emptiness, enums, id patterns, references, financial) is a later stage
 * (TASK-003). Pure and independently testable.
 */
public final class CompileService {

    public record Mapped(List<ValidationIssue> issues, Content content) {}

    private static final Set<String> FRONT =
            Set.of("sop_id", "title", "owner_team", "domain", "intent", "risk_level", "max_autonomy");
    private static final Set<String> INPUT = Set.of("name", "type");
    private static final Set<String> RULE = Set.of("id", "conditions", "action_ids");
    private static final Set<String> CONDITION = Set.of("input", "op", "value");
    private static final Set<String> ACTION = Set.of("id", "kind", "description", "max_amount");
    private static final Set<String> BOUNDARIES = Set.of("escalation");
    private static final Set<String> ESCALATION = Set.of("action_id", "input", "op", "amount", "target_action_id");
    private static final Set<String> MESSAGES = Set.of("primary", "escalation");

    @SuppressWarnings("unchecked")
    public Mapped map(ParsedSource ps) {
        List<ValidationIssue> s = new ArrayList<>();
        Map<Object, Object> fm = ps.frontMatterMap();
        if (fm == null) {
            s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "front matter must be a mapping/object", "frontmatter"));
            return new Mapped(s, null);
        }
        checkKeys(fm, FRONT, "frontmatter", s);
        String sopId = asText(fm.get("sop_id"), "frontmatter.sop_id", s);
        String title = asText(fm.get("title"), "frontmatter.title", s);
        String owner = asText(fm.get("owner_team"), "frontmatter.owner_team", s);
        String domain = asText(fm.get("domain"), "frontmatter.domain", s);
        String intent = asText(fm.get("intent"), "frontmatter.intent", s);
        String risk = asText(fm.get("risk_level"), "frontmatter.risk_level", s);
        String maxAuto = asText(fm.get("max_autonomy"), "frontmatter.max_autonomy", s);

        Policy policy = new Policy(bullets(ps, SectionKind.USE_WHEN), bullets(ps, SectionKind.DO_NOT_USE_WHEN));
        List<InputDecl> inputs = mapInputs(ps, s);
        List<Rule> rules = mapRules(ps, s);
        List<Action> actions = mapActions(ps, s);
        Boundaries boundaries = mapBoundaries(ps, s);
        CustomerMessages messages = mapMessages(ps, s);

        if (!s.isEmpty()) {
            return new Mapped(s, null);
        }
        Content content = new Content(sopId, title, owner, domain, intent, risk, maxAuto,
                policy, inputs, rules, actions, boundaries, messages);
        return new Mapped(List.of(), content);
    }

    private List<String> bullets(ParsedSource ps, SectionKind kind) {
        ParsedSection sec = ps.section(kind);
        return (sec != null && sec.bullets() != null) ? sec.bullets() : new ArrayList<>();
    }

    private List<InputDecl> mapInputs(ParsedSource ps, List<ValidationIssue> s) {
        List<InputDecl> out = new ArrayList<>();
        List<Object> list = asList(ps.section(SectionKind.INPUTS).value(), "Inputs Required", s);
        if (list == null) {
            return out;
        }
        for (int j = 0; j < list.size(); j++) {
            Map<Object, Object> m = asMap(list.get(j), "Inputs Required[" + j + "]", s);
            if (m == null) {
                continue;
            }
            checkKeys(m, INPUT, "Inputs Required[" + j + "]", s);
            String name = asText(m.get("name"), "Inputs Required[" + j + "].name", s);
            String type = asText(m.get("type"), "Inputs Required[" + j + "].type", s);
            out.add(new InputDecl(name, type));
        }
        return out;
    }

    private List<Rule> mapRules(ParsedSource ps, List<ValidationIssue> s) {
        List<Rule> out = new ArrayList<>();
        List<Object> list = asList(ps.section(SectionKind.RULES).value(), "Eligibility Rules", s);
        if (list == null) {
            return out;
        }
        for (int j = 0; j < list.size(); j++) {
            Map<Object, Object> m = asMap(list.get(j), "Eligibility Rules[" + j + "]", s);
            if (m == null) {
                continue;
            }
            String path = "Eligibility Rules[" + j + "]";
            checkKeys(m, RULE, path, s);
            String id = asText(m.get("id"), path + ".id", s);
            List<Condition> conditions = new ArrayList<>();
            List<Object> condList = asList(m.get("conditions"), path + ".conditions", s);
            if (condList != null) {
                for (int k = 0; k < condList.size(); k++) {
                    Map<Object, Object> cm = asMap(condList.get(k), path + ".conditions[" + k + "]", s);
                    if (cm == null) {
                        continue;
                    }
                    checkKeys(cm, CONDITION, path + ".conditions[" + k + "]", s);
                    String input = asText(cm.get("input"), path + ".conditions[" + k + "].input", s);
                    String op = asText(cm.get("op"), path + ".conditions[" + k + "].op", s);
                    Object value = cm.get("value"); // keep raw type fidelity (number or boolean)
                    conditions.add(new Condition(input, op, value));
                }
            }
            List<String> actionIds = new ArrayList<>();
            List<Object> ids = asList(m.get("action_ids"), path + ".action_ids", s);
            if (ids != null) {
                for (int k = 0; k < ids.size(); k++) {
                    actionIds.add(asText(ids.get(k), path + ".action_ids[" + k + "]", s));
                }
            }
            out.add(new Rule(id, conditions, actionIds));
        }
        return out;
    }

    private List<Action> mapActions(ParsedSource ps, List<ValidationIssue> s) {
        List<Action> out = new ArrayList<>();
        List<Object> list = asList(ps.section(SectionKind.ACTIONS).value(), "Actions", s);
        if (list == null) {
            return out;
        }
        for (int j = 0; j < list.size(); j++) {
            Map<Object, Object> m = asMap(list.get(j), "Actions[" + j + "]", s);
            if (m == null) {
                continue;
            }
            String path = "Actions[" + j + "]";
            checkKeys(m, ACTION, path, s);
            String id = asText(m.get("id"), path + ".id", s);
            String kind = asText(m.get("kind"), path + ".kind", s);
            String description = asText(m.get("description"), path + ".description", s);
            Double maxAmount = asPositiveFinite(m.get("max_amount"), path + ".max_amount", s);
            out.add(new Action(id, kind, description, maxAmount));
        }
        return out;
    }

    private Boundaries mapBoundaries(ParsedSource ps, List<ValidationIssue> s) {
        Map<Object, Object> m = asMap(ps.section(SectionKind.BOUNDARIES).value(), "Boundaries", s);
        if (m == null) {
            return new Boundaries(new ArrayList<>());
        }
        checkKeys(m, BOUNDARIES, "Boundaries", s);
        List<Escalation> esc = new ArrayList<>();
        List<Object> list = asList(m.get("escalation"), "Boundaries.escalation", s);
        if (list != null) {
            for (int j = 0; j < list.size(); j++) {
                Map<Object, Object> em = asMap(list.get(j), "Boundaries.escalation[" + j + "]", s);
                if (em == null) {
                    continue;
                }
                String path = "Boundaries.escalation[" + j + "]";
                checkKeys(em, ESCALATION, path, s);
                String actionId = asText(em.get("action_id"), path + ".action_id", s);
                String input = asText(em.get("input"), path + ".input", s);
                String op = asText(em.get("op"), path + ".op", s);
                double amount = asFiniteRequired(em.get("amount"), path + ".amount", s);
                String target = asText(em.get("target_action_id"), path + ".target_action_id", s);
                esc.add(new Escalation(actionId, input, op, amount, target));
            }
        }
        return new Boundaries(esc);
    }

    private CustomerMessages mapMessages(ParsedSource ps, List<ValidationIssue> s) {
        Map<Object, Object> m = asMap(ps.section(SectionKind.CUSTOMER_MESSAGES).value(), "Customer Messages", s);
        if (m == null) {
            return new CustomerMessages(null, null);
        }
        checkKeys(m, MESSAGES, "Customer Messages", s);
        String primary = asText(m.get("primary"), "Customer Messages.primary", s);
        String escalation = asText(m.get("escalation"), "Customer Messages.escalation", s);
        return new CustomerMessages(primary, escalation);
    }

    // ---- helpers ----

    private static void checkKeys(Map<Object, Object> m, Set<String> allowed, String path, List<ValidationIssue> s) {
        Set<String> keys = new LinkedHashSet<>();
        for (Object k : m.keySet()) {
            if (k instanceof String ks) {
                keys.add(ks);
            } else {
                s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "object keys must be strings", path));
                return;
            }
        }
        for (String k : keys) {
            if (!allowed.contains(k)) {
                s.add(ValidationIssue.of(UNKNOWN_KEY, "unknown key: " + k, path + (path.isEmpty() ? "" : "." + k)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> asMap(Object v, String path, List<ValidationIssue> s) {
        if (v instanceof Map<?, ?> m) {
            return (Map<Object, Object>) m;
        }
        if (v == null) {
            return null;
        }
        s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a mapping/object", path));
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object v, String path, List<ValidationIssue> s) {
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        if (v == null) {
            return null;
        }
        s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a list/array", path));
        return null;
    }

    private static String asText(Object v, String path, List<ValidationIssue> s) {
        if (v instanceof String str) {
            return str;
        }
        if (v == null) {
            return null;
        }
        s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a string", path));
        return null;
    }

    private static Double asPositiveFinite(Object v, String path, List<ValidationIssue> s) {
        if (v == null) {
            return null;
        }
        if (isFiniteNumber(v)) {
            return ((Number) v).doubleValue();
        }
        s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a positive finite number", path));
        return null;
    }

    private static double asFiniteRequired(Object v, String path, List<ValidationIssue> s) {
        if (v == null) {
            s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a positive finite number", path));
            return -1;
        }
        if (isFiniteNumber(v)) {
            return ((Number) v).doubleValue();
        }
        s.add(ValidationIssue.of(UNSUPPORTED_TYPE, "expected a positive finite number", path));
        return -1;
    }

    private static boolean isFiniteNumber(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d);
        }
        return false;
    }
}
