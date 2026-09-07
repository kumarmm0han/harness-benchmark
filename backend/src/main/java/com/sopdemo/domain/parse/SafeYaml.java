package com.sopdemo.domain.parse;

import static com.sopdemo.domain.issue.IssueCode.ALIASES_REJECTED;
import static com.sopdemo.domain.issue.IssueCode.CUSTOM_TAG_REJECTED;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_KEY;
import static com.sopdemo.domain.issue.IssueCode.INVALID_YAML;
import static com.sopdemo.domain.issue.IssueCode.NESTING_TOO_DEEP;
import static com.sopdemo.domain.issue.IssueCode.NON_FINITE_NUMBER;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Safe YAML → neutral Java tree (List/Map/scalar). Enforces FR-020 limits and NFR-020:
 * no aliases, no custom tags, bounded nesting (root = level 1), finite numbers, no
 * duplicate keys. Rejects unknown tag usage; only {@code null/bool/int/float/str/map/seq}
 * are permitted. Type fidelity is preserved (String stays String, boolean is boolean,
 * int/float are finite).
 */
public final class SafeYaml {

    private static final Set<Tag> SCALAR_TAGS = Set.of(Tag.NULL, Tag.BOOL, Tag.INT, Tag.FLOAT, Tag.STR);
    private static final Set<Tag> COLLECTION_TAGS = Set.of(Tag.MAP, Tag.SEQ);
    private static final Set<Tag> ALLOWED_TAGS = new HashSet<>();

    static {
        ALLOWED_TAGS.addAll(SCALAR_TAGS);
        ALLOWED_TAGS.addAll(COLLECTION_TAGS);
    }

    private SafeYaml() {}

    public record ParseLimits(int maxNesting) {
        public static final ParseLimits DEFAULT = new ParseLimits(20);
    }

    /**
     * Parse {@code text} into a neutral tree. Throws {@link YamlFailure} on any failure.
     * The caller (ParsingService) records the failure as a structural ValidationIssue for
     * the relevant path.
     */
    public static Object parse(String text, ParseLimits limits) {
        final Yaml yaml = new Yaml(new LoaderOptions());
        final Node root;
        try {
            root = yaml.compose(new StringReader(text == null ? "" : text));
        } catch (YAMLException e) {
            throw new YamlFailure(INVALID_YAML, e.getMessage());
        } catch (RuntimeException e) {
            throw new YamlFailure(INVALID_YAML, e.getMessage());
        }
        if (root == null) {
            throw new YamlFailure(INVALID_YAML, "empty or malformed YAML document");
        }
        // Track node identity to detect aliases (snakeyaml reuses the same Node object
        // for aliased anchors; a scalar that's reused at a second position is an alias).
        Set<Node> seen = new HashSet<>();
        return build(root, 1, limits, seen);
    }

    private static Object build(Node node, int depth, ParseLimits limits, Set<Node> seen) {
        if (!seen.add(node)) {
            throw new YamlFailure(ALIASES_REJECTED, "anchor/alias reference detected");
        }
        Tag tag = node.getTag();
        if (!ALLOWED_TAGS.contains(tag)) {
            throw new YamlFailure(CUSTOM_TAG_REJECTED, "custom or unsupported YAML tag: " + tag);
        }
        // `depth` is the collection (mapping/list) level, 1-based. Scalars inherit the
        // parent collection's depth and do not add a level (spec.md §1: root collection = 1).
        if (depth > limits.maxNesting()) {
            throw new YamlFailure(NESTING_TOO_DEEP, "YAML nesting level " + depth + " exceeds limit " + limits.maxNesting());
        }
        if (node instanceof ScalarNode scalar) {
            return coerceScalar(scalar);
        }
        if (node instanceof SequenceNode seq) {
            List<Object> out = new ArrayList<>();
            for (Node child : seq.getValue()) {
                out.add(build(child, depthFor(child, depth), limits, seen));
            }
            return out;
        }
        if (node instanceof MappingNode map) {
            return buildMapping(map, depth, limits, seen);
        }
        throw new YamlFailure(INVALID_YAML, "unsupported YAML node: " + node.getNodeId());
    }

    private static Object buildMapping(MappingNode node, int depth, ParseLimits limits, Set<Node> seen) {
        LinkedHashMap<Object, Object> out = new LinkedHashMap<>();
        Set<Object> seenKeys = new HashSet<>();
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Object key = keyNode instanceof ScalarNode ks
                    ? coerceScalar(ks)
                    : build(keyNode, depthFor(keyNode, depth), limits, seen); // collection keys allowed but unusual
            if (!seenKeys.add(key)) {
                throw new YamlFailure(DUPLICATE_KEY, "duplicate key: " + key);
            }
            Object value = build(tuple.getValueNode(), depthFor(tuple.getValueNode(), depth), limits, seen);
            out.put(key, value);
        }
        return out;
    }

    private static int depthFor(Node child, int parentDepth) {
        org.yaml.snakeyaml.nodes.NodeId id = child.getNodeId();
        return (id == org.yaml.snakeyaml.nodes.NodeId.mapping || id == org.yaml.snakeyaml.nodes.NodeId.sequence)
                ? parentDepth + 1
                : parentDepth;
    }

    @SuppressWarnings("unchecked")
    private static Object coerceScalar(ScalarNode node) {
        Tag tag = node.getTag();
        String v = node.getValue();
        if (tag.equals(Tag.NULL)) return null;
        if (tag.equals(Tag.BOOL)) return toBoolean(v);
        if (tag.equals(Tag.INT))  return toLong(v);
        if (tag.equals(Tag.FLOAT)) return toFloat(v);
        return v == null ? "" : v;
    }

    private static Boolean toBoolean(String v) {
        if (v == null) return null;
        switch (v.toLowerCase()) {
            case "true", "yes", "y", "on":  return Boolean.TRUE;
            case "false", "no", "n", "off": return Boolean.FALSE;
            default: throw new YamlFailure(INVALID_YAML, "invalid boolean literal: " + v);
        }
    }

    private static Long toLong(String v) {
        if (v == null) return null;
        // YAML 1.1-style int literals: allow base-60 / hex / oct as a fallback, but
        // restrict the common cases to 10-digit decimal within long range.
        String t = v.trim();
        if (t.startsWith("0x") || t.startsWith("-0x")) {
            try { return Long.decode(t); } catch (NumberFormatException e) { }
        }
        try { return Long.parseLong(t); } catch (NumberFormatException e) { }
        try { return Long.decode(t); } catch (NumberFormatException e) { }
        throw new YamlFailure(INVALID_YAML, "invalid integer literal: " + v);
    }

    private static Double toFloat(String v) {
        if (v == null) return null;
        // Reject infinite / NaN literals explicitly (FR-020, NFR-020).
        String t = v.trim();
        if (t.equalsIgnoreCase(".inf") || t.equalsIgnoreCase("+.inf") || t.equalsIgnoreCase("-.inf")
                || t.equalsIgnoreCase(".nan")) {
            throw new YamlFailure(NON_FINITE_NUMBER, "non-finite number: " + v);
        }
        try {
            double d = Double.parseDouble(t);
            if (!Double.isFinite(d)) {
                throw new YamlFailure(NON_FINITE_NUMBER, "non-finite number: " + v);
            }
            return d;
        } catch (NumberFormatException e) {
            throw new YamlFailure(INVALID_YAML, "invalid floating literal: " + v);
        }
    }

    @SuppressWarnings("unused")
    private static Set<Tag> scalarTags() {
        return SCALAR_TAGS;
    }

    @SuppressWarnings("unused")
    private static Set<Tag> collectionTags() {
        return COLLECTION_TAGS;
    }
}
