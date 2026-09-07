package com.sopdemo.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a source document into front matter + the seven spec sections (spec.md section 1, DES-104).
 * Structural rules enforced here:
 * <ul>
 *   <li>front matter between {@code ---} delimiters; must load as a mapping</li>
 *   <li>exactly the seven level-two headings, each once, in spec order</li>
 *   <li>bullet sections contain one or more {@code - } bullet lines only</li>
 *   <li>machine sections contain exactly one fenced {@code yaml} block and no other nonblank content</li>
 *   <li>YAML is loaded safely with aliases/duplicate keys/custom tags rejected, finite numbers, depth limit</li>
 * </ul>
 */
public final class SectionParser {

    public static final List<String> EXPECTED_SECTIONS = List.of(
            "Intent (When to use)",
            "Do Not Use When",
            "Inputs Required",
            "Eligibility Rules",
            "Actions",
            "Boundaries",
            "Customer Messages");

    private static final List<String> PROSE_SECTIONS = List.of("Intent (When to use)", "Do Not Use When");

    private SectionParser() {
    }

    public static ParsedDoc parse(String source) {
        List<Issue> issues = new ArrayList<>();
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));

        Map<String, Object> frontMatter = null;
        int bodyStart = 0;

        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            issues.add(Issue.structural(Codes.MISSING_FRONTMATTER,
                    "The document must begin with a '---' YAML front matter delimiter.", "frontmatter"));
        } else {
            Integer close = null;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("---")) {
                    close = i;
                    break;
                }
            }
            if (close == null) {
                issues.add(Issue.structural(Codes.MISSING_FRONTMATTER,
                        "The YAML front matter is not closed by a '---' delimiter line.", "frontmatter"));
            } else {
                String fmText = String.join("\n", lines.subList(1, close));
                YamlSafe.Loaded fm = YamlSafe.load(fmText, "frontmatter");
                issues.addAll(fm.issues());
                if (fm.issues().isEmpty()) {
                    Map<String, Object> mapping = YamlSafe.asMapping(fm.value());
                    if (mapping == null) {
                        issues.add(Issue.structural(Codes.FRONTMATTER_NOT_MAPPING,
                                "The YAML front matter must be a single mapping of fields.", "frontmatter"));
                    } else {
                        frontMatter = mapping;
                    }
                }
                bodyStart = close + 1;
            }
        }

        List<String> body = lines.subList(bodyStart, lines.size());
        List<String> headingOrder = new ArrayList<>();

        // Locate level-two headings of the body.
        List<SectionChunk> chunks = new ArrayList<>();
        boolean preambleNonblank = false;
        int i = 0;
        while (i < body.size()) {
            String line = body.get(i);
            if (isHeading2(line)) {
                String name = line.substring(3).trim();
                headingOrder.add(name);
                int j = i + 1;
                List<String> content = new ArrayList<>();
                while (j < body.size() && !isHeading2(body.get(j))) {
                    content.add(body.get(j));
                    j++;
                }
                chunks.add(new SectionChunk(name, content));
                i = j;
            } else {
                if (!line.trim().isEmpty()) {
                    preambleNonblank = true;
                }
                i++;
            }
        }
        if (preambleNonblank) {
            issues.add(Issue.structural(Codes.UNKNOWN_SECTION,
                    "Nonblank content before the first level-two heading is not supported.", "preamble"));
        }

        Map<String, SectionChunk> chunkByName = new LinkedHashMap<>();
        for (SectionChunk chunk : chunks) {
            SectionChunk existing = chunkByName.put(chunk.name(), chunk);
            if (existing != null) {
                issues.add(Issue.structural(Codes.MISSING_SECTION,
                        "Section '" + chunk.name() + "' appears more than once; each section must appear exactly once.",
                        "section:" + chunk.name()));
            }
        }

        for (String expected : EXPECTED_SECTIONS) {
            if (!chunkByName.containsKey(expected)) {
                issues.add(Issue.structural(Codes.MISSING_SECTION,
                        "Required section '" + expected + "' is missing.", "section:" + expected));
            }
        }
        for (String seen : headingOrder) {
            if (!EXPECTED_SECTIONS.contains(seen)) {
                issues.add(Issue.structural(Codes.UNKNOWN_SECTION,
                        "Section '" + seen + "' is not part of the supported document format.", "section:" + seen));
            }
        }
        List<String> expectedSeen = new ArrayList<>();
        for (String section : EXPECTED_SECTIONS) {
            if (chunkByName.containsKey(section)) {
                expectedSeen.add(section);
            }
        }
        if (!expectedSeen.equals(headingOrder.stream().filter(EXPECTED_SECTIONS::contains).toList())) {
            issues.add(Issue.structural(Codes.SECTION_ORDER,
                    "Sections must appear in this order: " + String.join(", ", EXPECTED_SECTIONS) + ".", "sections"));
        }

        // Resolve each section body.
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, List<String>> prose = new LinkedHashMap<>();
        for (SectionChunk chunk : chunks) {
            String path = "section:" + chunk.name();
            if (PROSE_SECTIONS.contains(chunk.name())) {
                List<String> bullets = new ArrayList<>();
                boolean malformed = false;
                boolean anyNonblank = false;
                for (String line : chunk.content()) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    anyNonblank = true;
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("- ") && !trimmed.equals("-")) {
                        malformed = true;
                        continue;
                    }
                    bullets.add(trimmed.substring(2).trim());
                }
                if (malformed) {
                    issues.add(Issue.structural(Codes.INVALID_BULLET_SECTION,
                            "Section '" + chunk.name() + "' must contain only plain bullet lines ('- text').", path));
                }
                if (anyNonblank && bullets.isEmpty()) {
                    issues.add(Issue.structural(Codes.EMPTY_SECTION,
                            "Section '" + chunk.name() + "' must contain at least one bullet line.", path));
                }
                if (!bullets.isEmpty()) {
                    prose.put(chunk.name(), bullets);
                }
            } else if (EXPECTED_SECTIONS.contains(chunk.name())) {
                YamlBlock block = extractSingleYamlBlock(chunk.name(), chunk.content(), issues);
                if (block != null) {
                    YamlSafe.Loaded loaded = YamlSafe.load(block.body(), path);
                    issues.addAll(loaded.issues());
                    if (loaded.issues().isEmpty()) {
                        resolved.put(chunk.name(), loaded.value());
                    }
                }
            }
        }

        return new ParsedDoc(
                frontMatter,
                prose.getOrDefault("Intent (When to use)", List.of()),
                prose.getOrDefault("Do Not Use When", List.of()),
                resolved.get("Inputs Required"),
                resolved.get("Eligibility Rules"),
                resolved.get("Actions"),
                resolved.get("Boundaries"),
                resolved.get("Customer Messages"),
                headingOrder,
                issues);
    }

    private record SectionChunk(String name, List<String> content) {
    }

    private record YamlBlock(String body) {
    }

    private static YamlBlock extractSingleYamlBlock(String name, List<String> content, List<Issue> issues) {
        String path = "section:" + name;
        int open = -1;
        List<String> outOfBlock = new ArrayList<>();
        boolean inBlock = false;
        List<String> body = new ArrayList<>();
        int close = -1;
        for (int i = 0; i < content.size(); i++) {
            String trimmed = content.get(i).trim();
            if (!inBlock && trimmed.startsWith("```yaml")) {
                if (open != -1) {
                    outOfBlock.add(content.get(i));
                    continue;
                }
                open = i;
                inBlock = true;
            } else if (inBlock && trimmed.equals("```")) {
                close = i;
                inBlock = false;
            } else if (inBlock) {
                body.add(content.get(i));
            } else if (trimmed.isEmpty()) {
                // blank lines are allowed
            } else {
                outOfBlock.add(content.get(i));
            }
        }
        if (open == -1) {
            issues.add(Issue.structural(Codes.INVALID_MACHINE_SECTION,
                    "Section '" + name + "' must contain exactly one fenced 'yaml' block.", path));
            return null;
        }
        if (inBlock) {
            issues.add(Issue.structural(Codes.INVALID_MACHINE_SECTION,
                    "The fenced 'yaml' block in '" + name + "' is not closed.", path));
            return null;
        }
        if (!outOfBlock.isEmpty()) {
            issues.add(Issue.structural(Codes.INVALID_MACHINE_SECTION,
                    "Section '" + name + "' must contain only the fenced 'yaml' block and blank lines.", path));
            return null;
        }
        return new YamlBlock(String.join("\n", body));
    }

    private static boolean isHeading2(String line) {
        return line.startsWith("## ") && !line.startsWith("### ");
    }
}
