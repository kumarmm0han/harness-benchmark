package com.sopdemo.domain.validate;

import com.sopdemo.domain.PolicyResult;
import com.sopdemo.domain.compile.CompileService;
import com.sopdemo.domain.issue.ValidationIssue;
import com.sopdemo.domain.model.Content;
import com.sopdemo.domain.parse.ParsedSource;
import com.sopdemo.domain.parse.ParsingService;
import com.sopdemo.domain.parse.SafeYaml;
import java.util.ArrayList;
import java.util.List;

/**
 * Validation pipeline (ARC-003, FR-020/021/030/032/034) — pure, independent of Spring/DB
 * (PRN-004, PRN-005). Tiered: parse → compile → structural → (semantic references ∪
 * financial). A structural failure stops the pipeline (semantic omitted per FR-034).
 * Identical source yields identical content and issues; content is null whenever invalid.
 */
public final class ValidationService {

    private final ParsingService parse;
    private final CompileService compile = new CompileService();
    private final StructuralValidator structural = new StructuralValidator();
    private final ReferenceValidator references = new ReferenceValidator();
    private final FinancialSafety financial = new FinancialSafety();

    public ValidationService(SafeYaml.ParseLimits limits) {
        this.parse = new ParsingService(limits);
    }

    public PolicyResult validate(String source) {
        ParsedSource ps = parse.parse(source);
        if (!ps.issues().isEmpty()) {
            return invalid(ps.issues());
        }
        CompileService.Mapped mapped = compile.map(ps);
        if (!mapped.issues().isEmpty()) {
            return invalid(mapped.issues());
        }
        Content content = mapped.content();
        List<ValidationIssue> structuralIssues = structural.validate(content);
        if (!structuralIssues.isEmpty()) {
            return invalid(structuralIssues);
        }
        List<ValidationIssue> semantic = new ArrayList<>();
        semantic.addAll(references.validate(content));
        semantic.addAll(financial.validate(content));
        if (!semantic.isEmpty()) {
            return invalid(semantic);
        }
        return new PolicyResult(true, List.of(), content);
    }

    private static PolicyResult invalid(List<ValidationIssue> issues) {
        List<ValidationIssue> sorted = issues.stream().sorted(ValidationIssue.STABLE_ORDER).toList();
        return new PolicyResult(false, sorted, null);
    }
}
