package com.sopdemo.domain.parse;

import com.sopdemo.domain.issue.ValidationIssue;
import java.util.List;
import java.util.Map;

/**
 * Neutral parse result (ARC-003 stage 1). `frontMatter` is the parsed front-matter value
 * (expected to be a mapping); `sections` the neutral values per section; `issues` holds
 * document-structure and safe-YAML structural issues. It carries no policy interpretation —
 * mapping and validation run later so each stage stays independently testable (PRN-004, PRN-005).
 */
public record ParsedSource(
        Object frontMatter,
        Map<SectionKind, ParsedSection> sections,
        List<ValidationIssue> issues) {

    public ParsedSection section(SectionKind kind) {
        return sections.get(kind);
    }

    public Map<Object, Object> frontMatterMap() {
        return (frontMatter instanceof Map<?, ?> m) ? (Map<Object, Object>) m : null;
    }
}
