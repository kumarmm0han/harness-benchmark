package com.sop.domain;

import com.sop.dto.Issue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The deterministic validation pipeline (DES-005). A single entry point:
 *   validate(source) → PipelineResult
 *
 * <p>Order and rules:
 * <ol>
 *   <li>size check (source bytes ≤ 64 KiB).</li>
 *   <li>section split (DES-004).</li>
 *   <li>front matter + machine values parsed via {@link SafeYaml} / {@link
 *       DocumentSplitter}.</li>
 *   <li>front matter validation (FR-001, enums, refund-domain).</li>
 *   <li>shape validation (FR-020, FR-030).</li>
 *   <li>reference + financial validation (FR-030, FR-032).</li>
 *   <li>only if zero issues: build the canonical content (DES-006).</li>
 * </ol>
 *
 * <p>Issues are deduplicated and sorted by (path, code) for a stable,
 * deterministic response (PRN-005 / FR-034 "stable ordering by source path
 * then code").
 */
public final class ValidatorPipeline {

  private ValidatorPipeline() {}

  public static PipelineResult validate(String source) {
    List<Issue> issues = new ArrayList<>();

    // 1. size
    int bytes = source == null ? 0 : source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (source == null || source.isBlank()) {
      issues.add(Issue.structural(Contract.C_SOURCE_TOO_LARGE,
          "source is empty", null));
      return new PipelineResult(false, issues, null, null);
    }
    if (bytes > Contract.SOURCE_BYTES_MAX) {
      issues.add(Issue.structural(Contract.C_SOURCE_TOO_LARGE,
          "source exceeds " + Contract.SOURCE_BYTES_MAX + " UTF-8 bytes", null));
      return new PipelineResult(false, issues, null, null);
    }

    // 2+3. section split + parse front matter & machine sections
    ParsedDocument doc = DocumentSplitter.splitAndParse(source);
    issues.addAll(doc.structuralIssues);

    // 4. front matter
    FrontMatterValidator.validate(doc.frontMatter, issues);
    FrontMatterValidator.validateRefundDomain(doc.frontMatter, issues);

    // 5. shape (only if front matter has known keys — otherwise we may not have sections)
    ShapeValidator.validateInputs(doc.machineValues.get(Contract.SEC_INPUTS), issues);
    ShapeValidator.validateRules(doc.machineValues.get(Contract.SEC_RULES), issues);
    ShapeValidator.validateActions(doc.machineValues.get(Contract.SEC_ACTIONS), issues);
    if (doc.machineValues.containsKey(Contract.SEC_BOUNDARIES)) {
      ShapeValidator.validateBoundaries(doc.machineValues.get(Contract.SEC_BOUNDARIES), issues);
    }
    if (doc.machineValues.containsKey(Contract.SEC_MESSAGES)) {
      ShapeValidator.validateMessages(doc.machineValues.get(Contract.SEC_MESSAGES), issues);
    }

    // 6. references / financial
    ReferenceValidator.validate(doc, issues);

    // Dedupe + sort by (path, code)
    return new PipelineResult(
        issues.isEmpty(),
        dedupeAndSort(issues),
        null, // content computed by controller when issues is empty
        doc);
  }

  /** Build the canonical content (DES-006). Must be called only when issues are empty. */
  public static Content buildContent(ParsedDocument doc) {
    return CanonicalBuilder.build(doc);
  }

  private static List<Issue> dedupeAndSort(List<Issue> in) {
    var seen = new java.util.LinkedHashSet<Issue>();
    for (Issue i : in) seen.add(i);
    List<Issue> out = new ArrayList<>(seen);
    out.sort(Comparator
        .comparing(Issue::path, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(Issue::code, Comparator.nullsFirst(Comparator.naturalOrder())));
    return List.copyOf(out);
  }
}
