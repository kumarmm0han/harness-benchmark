package com.sopdemo.domain.parse;

import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_SECTION;
import static com.sopdemo.domain.issue.IssueCode.FENCE_STRUCTURE_INVALID;
import static com.sopdemo.domain.issue.IssueCode.INVALID_YAML;
import static com.sopdemo.domain.issue.IssueCode.MISSING_SECTION;
import static com.sopdemo.domain.issue.IssueCode.PROSE_STRUCTURE_INVALID;
import static com.sopdemo.domain.issue.IssueCode.UNKNOWN_SECTION;

import com.sopdemo.domain.issue.ValidationIssue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document-structure parsing (ARC-003 stage 1, FR-020): split front matter and the seven
 * required sections, validate prose bullets, extract the single {@code yaml} fence per
 * machine section, and run safe-YAML. Emits structural issues; produces neutral values for
 * mapping/validation. Pure and independent of Spring (PRN-004, PRN-005).
 */
public final class ParsingService {

    private static final Pattern HEADING = Pattern.compile("^## +(.+)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+] +(.*)$");
    private static final Pattern FENCE_OPEN = Pattern.compile("^\\s*`{3,}\\s*[Yy][Aa][Mm][Ll]\\s*$");
    private static final Pattern FENCE_CLOSE = Pattern.compile("^\\s*`{3,}\\s*$");

    private final SafeYaml.ParseLimits limits;

    public ParsingService(SafeYaml.ParseLimits limits) {
        this.limits = limits;
    }

    public ParsedSource parse(String source) {
        List<String> lines = split(source);
        List<ValidationIssue> issues = new ArrayList<>();
        int n = lines.size();

        // --- front matter ---
        Object frontMatter = null;
        int i = 0;
        while (i < n && lines.get(i).isBlank()) {
            i++;
        }
        if (i >= n || !lines.get(i).trim().equals("---")) {
            issues.add(ValidationIssue.of(INVALID_YAML, "document must begin with a --- front-matter delimiter", "frontmatter"));
        } else {
            i++;
            List<String> fm = new ArrayList<>();
            boolean closed = false;
            while (i < n) {
                if (lines.get(i).trim().equals("---")) {
                    closed = true;
                    i++;
                    break;
                }
                fm.add(lines.get(i));
                i++;
            }
            if (!closed) {
                issues.add(ValidationIssue.of(INVALID_YAML, "front matter is not terminated by a --- delimiter", "frontmatter"));
            } else {
                frontMatter = parseFront(fm, issues);
            }
        }

        // --- discover sections (parallel title/body lists) ---
        List<String> titles = new ArrayList<>();
        List<List<String>> bodies = new ArrayList<>();
        for (String line : lines.subList(i, n)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                titles.add(m.group(1).trim());
                bodies.add(new ArrayList<>());
            } else if (!line.isBlank()) {
                if (bodies.isEmpty()) {
                    issues.add(ValidationIssue.of(PROSE_STRUCTURE_INVALID,
                            "content before the first section heading is not allowed", "(preamble)"));
                } else {
                    bodies.get(bodies.size() - 1).add(line);
                }
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String t : titles) {
            counts.merge(t, 1, Integer::sum);
        }
        Map<String, List<String>> firstBody = new LinkedHashMap<>();
        for (int j = 0; j < titles.size(); j++) {
            firstBody.putIfAbsent(titles.get(j), bodies.get(j));
        }

        // unknown sections (each distinct unknown title once)
        for (String t : titles) {
            if (!SectionKind.BY_TITLE.containsKey(t)) {
                issues.add(ValidationIssue.of(UNKNOWN_SECTION, "unknown section: " + t, t));
            }
        }

        Map<SectionKind, ParsedSection> sections = new EnumMap<>(SectionKind.class);
        for (SectionKind kind : SectionKind.values()) {
            String title = kind.title();
            int c = counts.getOrDefault(title, 0);
            if (c == 0) {
                issues.add(ValidationIssue.of(MISSING_SECTION, "required section is missing: " + title, title));
                continue;
            }
            if (c > 1) {
                issues.add(ValidationIssue.of(DUPLICATE_SECTION, "section appears more than once: " + title, title));
                continue;
            }
            ParsedSection ps = parseSection(kind, firstBody.get(title), issues);
            if (ps != null) {
                sections.put(kind, ps);
            }
        }

        return new ParsedSource(frontMatter, sections, issues);
    }

    private Object parseFront(List<String> fm, List<ValidationIssue> issues) {
        String text = String.join("\n", fm);
        if (text.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return SafeYaml.parse(text, limits);
        } catch (YamlFailure e) {
            issues.add(ValidationIssue.of(e.code(), e.getMessage(), "frontmatter"));
            return null;
        }
    }

    private ParsedSection parseSection(SectionKind kind, List<String> body, List<ValidationIssue> issues) {
        String path = kind.title();
        if (kind.shape() == SectionKind.Shape.PROSE) {
            return parseProse(kind, body, issues, path);
        }
        return parseMachine(kind, body, issues, path);
    }

    private ParsedSection parseProse(SectionKind kind, List<String> body, List<ValidationIssue> issues, String path) {
        List<String> bullets = new ArrayList<>();
        for (String line : body) {
            if (line.isBlank()) {
                continue;
            }
            Matcher m = BULLET.matcher(line);
            if (m.matches()) {
                String text = m.group(1).trim();
                if (!text.isEmpty()) {
                    bullets.add(text);
                }
            } else {
                issues.add(ValidationIssue.of(PROSE_STRUCTURE_INVALID,
                        "prose section may only contain bullet lines (found non-bullet content)", path));
                break;
            }
        }
        return new ParsedSection(kind, bullets, null);
    }

    private ParsedSection parseMachine(SectionKind kind, List<String> body, List<ValidationIssue> issues, String path) {
        int state = 0; // 0 before-fence, 1 in-fence, 2 after-fence
        StringBuilder inner = new StringBuilder();
        for (String line : body) {
            if (state == 0) {
                if (line.isBlank()) {
                    continue;
                }
                if (FENCE_OPEN.matcher(line).matches()) {
                    state = 1;
                } else {
                    issues.add(ValidationIssue.of(FENCE_STRUCTURE_INVALID,
                            "machine section must begin with a ```yaml fenced block", path));
                    return null;
                }
            } else if (state == 1) {
                if (FENCE_CLOSE.matcher(line).matches()) {
                    state = 2;
                } else {
                    inner.append(line).append("\n");
                }
            } else {
                if (line.isBlank()) {
                    continue;
                }
                issues.add(ValidationIssue.of(FENCE_STRUCTURE_INVALID,
                        "only one ```yaml block is allowed in a machine section", path));
                return null;
            }
        }
        if (state == 0) {
            issues.add(ValidationIssue.of(FENCE_STRUCTURE_INVALID,
                    "machine section must contain exactly one ```yaml block", path));
            return null;
        }
        if (state == 1) {
            issues.add(ValidationIssue.of(FENCE_STRUCTURE_INVALID,
                    "the ```yaml block is not terminated with its closing fence", path));
            return null;
        }
        try {
            return new ParsedSection(kind, null, SafeYaml.parse(inner.toString(), limits));
        } catch (YamlFailure e) {
            issues.add(ValidationIssue.of(e.code(), e.getMessage(), path));
            return null;
        }
    }

    private static List<String> split(String source) {
        String s = source == null ? "" : source;
        if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        return new ArrayList<>(List.of(s.split("\n", -1)));
    }
}
