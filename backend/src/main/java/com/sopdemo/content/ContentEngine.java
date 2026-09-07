package com.sopdemo.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * The pure content pipeline (DES-102):
 * size gate -> section parse (safe YAML) -> structural validation -> semantic validation
 * (including financial-safety invariants) -> canonical compile.
 *
 * <p>Runs deterministically on a CPU-bound thread; the engine is stateless and thread-safe.</p>
 */
@Service
public final class ContentEngine {

    /**
     * Result of analysis. When {@code valid=false}, {@code content} is null and {@code issues} is
     * nonempty, sorted by (path, code) for stable output (NFR-020).
     */
    public record ContentAnalysis(boolean valid, List<Issue> issues, Map<String, Object> content) {

        public static ContentAnalysis valid(Map<String, Object> content) {
            return new ContentAnalysis(true, List.of(), content);
        }

        public static ContentAnalysis invalid(List<Issue> issues) {
            List<Issue> sorted = new ArrayList<>(issues);
            sorted.sort(Comparator.comparing(Issue::path)
                    .thenComparing(Issue::code)
                    .thenComparing(Issue::message));
            return new ContentAnalysis(false, List.copyOf(sorted), null);
        }
    }

    private final int maxSourceBytes;

    public ContentEngine() {
        this(SourceLimits.MAX_SOURCE_BYTES);
    }

    public ContentEngine(int maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public ContentAnalysis analyze(String source) {
        if (source == null) {
            return ContentAnalysis.invalid(List.of(Issue.structural(
                    Codes.MISSING_FRONTMATTER, "Request body must contain a source string.", "source")));
        }
        SourceLimits.check(source, maxSourceBytes); // may throw SourceTooLargeException
        ParsedDoc doc = SectionParser.parse(source);
        if (!doc.structurallyUsable()) {
            return ContentAnalysis.invalid(doc.issues());
        }
        List<Issue> structural = new ArrayList<>();
        structural.addAll(StructuralValidator.validate(doc));
        if (!structural.isEmpty()) {
            return ContentAnalysis.invalid(structural);
        }
        List<Issue> semantic = new ArrayList<>();
        SemanticValidator.checkNonEmpty(doc, semantic);
        SemanticValidator.Context ctx = SemanticValidator.buildContext(doc, semantic);
        semantic.addAll(SemanticValidator.validateOnly(doc, ctx));
        semantic.addAll(FinancialSafety.check(doc, ctx));
        if (!semantic.isEmpty()) {
            return ContentAnalysis.invalid(semantic);
        }
        return ContentAnalysis.valid(Compiler.compile(doc));
    }
}
