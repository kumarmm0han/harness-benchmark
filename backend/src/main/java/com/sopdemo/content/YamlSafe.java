package com.sopdemo.content;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Safe YAML load (FR-020, PRN-004):
 * <ul>
 *   <li>restricted to safe core types (no Java object construction - never executes content)</li>
 *   <li>aliases rejected</li>
 *   <li>duplicate keys rejected</li>
 *   <li>custom tags rejected (safe constructor only)</li>
 *   <li>YAML collection nesting capped at 20 levels (root collection = level 1)</li>
 *   <li>non-finite numbers rejected</li>
 * </ul>
 * Failures are returned as controlled {@link Issue}s (PRN-005); the path prefix is
 * supplied by the caller (e.g. {@code frontmatter} or the section name).
 */
public final class YamlSafe {

    public static final int MAX_NESTING_DEPTH = 20;

    private YamlSafe() {
    }

    /** Result of a safe load: a value (may be null for empty input) plus structural issues. */
    public record Loaded(Object value, List<Issue> issues) {
    }

    public static Loaded load(String yamlText, String pathPrefix) {
        Object value;
        try {
            value = parse(yamlText);
        } catch (AliasesNotPermittedException e) {
            return new Loaded(null, List.of(Issue.structural(Codes.ALIAS_OR_TAG,
                    "YAML aliases are not allowed in this document.", pathPrefix)));
        } catch (StackOverflowError error) {
            return new Loaded(null, List.of(structural(Codes.NESTING_TOO_DEEP,
                    "YAML nesting exceeds the " + MAX_NESTING_DEPTH + " level limit.", pathPrefix, error)));
        } catch (RuntimeException error) {
            return new Loaded(null, List.of(structural(classify(error), classifyMessage(error), pathPrefix, error)));
        }
        List<Issue> issues = new ArrayList<>();
        if (maxDepth(value) > MAX_NESTING_DEPTH) {
            issues.add(Issue.structural(Codes.NESTING_TOO_DEEP,
                    "YAML nesting exceeds the " + MAX_NESTING_DEPTH + " level limit.", pathPrefix));
        }
        collectNonFinite(value, pathPrefix, issues);
        return new Loaded(value, issues);
    }

    private static Issue structural(String code, String message, String path, Throwable cause) {
        Issue issue = Issue.structural(code, message, path);
        return issue; // cause is intentionally not exposed to clients (NFR-020)
    }

    private static Object parse(String yamlText) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        rejectAllAliases(yaml, yamlText);
        return yaml.loadAs(yamlText, Object.class);
    }

    /** Rejection marker: any alias (scalar or collection) is prohibited (FR-020). */
    static final class AliasesNotPermittedException extends RuntimeException {
    }

    private static void rejectAllAliases(Yaml yaml, String yamlText) {
        try (Reader reader = new StringReader(yamlText)) {
            for (org.yaml.snakeyaml.events.Event event : yaml.parse(reader)) {
                if (event instanceof org.yaml.snakeyaml.events.AliasEvent) {
                    throw new AliasesNotPermittedException();
                }
            }
        } catch (IOException e) {
            throw new AliasesNotPermittedException(); // defensive; never reachable for in-memory input
        }
    }

    static String classify(RuntimeException error) {
        String chain = errorChain(error).toLowerCase();
        if (chain.contains("alias")) {
            return Codes.ALIAS_OR_TAG;
        }
        if (chain.contains("duplicate key")) {
            return Codes.DUPLICATE_KEY;
        }
        return Codes.MALFORMED_YAML;
    }

    static String classifyMessage(RuntimeException error) {
        String chain = errorChain(error).toLowerCase();
        if (chain.contains("alias")) {
            return "YAML aliases are not allowed in this document.";
        }
        if (chain.contains("duplicate key")) {
            return "A YAML mapping contains a duplicate key.";
        }
        return "The YAML block is malformed or uses unsupported tags.";
    }

    private static String errorChain(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable t = error;
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    /** Iterative depth scan (root collection = level 1; scalars do not add depth). */
    static int maxDepth(Object node) {
        int max = 0;
        Deque<Object> nodes = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        if (node instanceof Map<?, ?> || node instanceof List<?>) {
            nodes.push(node);
            depths.push(1);
        } else if (node != null) {
            nodes.push(node);
            depths.push(0);
        }
        while (!nodes.isEmpty()) {
            Object current = nodes.pop();
            int depth = depths.pop();
            max = Math.max(max, depth);
            if (current instanceof Map<?, ?> map) {
                for (Object value : map.values()) {
                    if (value != null) {
                        nodes.push(value);
                        depths.push(value instanceof Map<?, ?> || value instanceof List<?> ? depth + 1 : depth);
                    }
                }
            } else if (current instanceof List<?> list) {
                for (Object value : list) {
                    if (value != null) {
                        nodes.push(value);
                        depths.push(value instanceof Map<?, ?> || value instanceof List<?> ? depth + 1 : depth);
                    }
                }
            }
        }
        return max;
    }

    private static void collectNonFinite(Object node, String path, List<Issue> issues) {
        if (node instanceof Number number && !(number instanceof BigInteger) && !(number instanceof BigDecimal)) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                issues.add(Issue.structural(Codes.NON_FINITE_NUMBER,
                        "Numeric values must be finite.", path.isEmpty() ? "value" : path));
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String child = path.isEmpty() ? "." + entry.getKey() : path + "." + entry.getKey();
                collectNonFinite(entry.getValue(), child, issues);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String child = path + "[" + i + "]";
                collectNonFinite(list.get(i), child, issues);
            }
        }
    }

    public static Map<String, Object> asMapping(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    out.put(key, v);
                }
            });
            return out;
        }
        return null;
    }

    public static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return null;
    }
}
