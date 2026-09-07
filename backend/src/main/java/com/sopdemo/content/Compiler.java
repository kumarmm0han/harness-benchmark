package com.sopdemo.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a fully validated document into the canonical content shape (spec.md section 4, DES-102).
 * Field order is deterministic (insertion order), which keeps serialized snapshots stable.
 */
final class Compiler {

    private Compiler() {
    }

    static Map<String, Object> compile(ParsedDoc doc) {
        Map<String, Object> fm = doc.frontMatter();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("sop_id", fm.get("sop_id"));
        content.put("title", fm.get("title"));
        content.put("owner_team", fm.get("owner_team"));
        content.put("domain", fm.get("domain"));
        content.put("intent", fm.get("intent"));
        content.put("risk_level", fm.get("risk_level"));
        content.put("max_autonomy", fm.get("max_autonomy"));

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("use_when", List.copyOf(doc.useWhen()));
        policy.put("do_not_use_when", List.copyOf(doc.doNotUseWhen()));
        content.put("policy", policy);

        content.put("inputs", asObjectList(doc.inputs()));
        content.put("rules", asObjectList(doc.rules()));
        content.put("actions", asObjectList(doc.actions()));
        content.put("boundaries", doc.boundaries());
        content.put("customer_messages", asObjectMap(doc.customerMessages()));
        return content;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjectList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObjectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
