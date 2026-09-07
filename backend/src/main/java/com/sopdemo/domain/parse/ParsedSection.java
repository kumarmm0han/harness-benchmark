package com.sopdemo.domain.parse;

import java.util.List;

/**
 * A single parsed section. Prose sections carry bullet {@code strings} (inline markup
 * retained verbatim as data); machine sections carry a neutral {@code value} — a list or
 * mapping produced by the safe YAML parser. Never executed (NFR-020).
 */
public record ParsedSection(SectionKind kind, List<String> bullets, Object value) {
    public boolean isProse() {
        return kind.shape() == SectionKind.Shape.PROSE;
    }
}
