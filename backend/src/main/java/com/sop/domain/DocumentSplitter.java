package com.sop.domain;

import com.sop.dto.Issue;

import java.util.List;
import java.util.Map;

/**
 * Splits the source document into a ParsedDocument (DES-004).
 * Runs the section parser, then SafeYaml on each machine block, collecting
 * structural issues. This is the input to the validators in DES-005.
 */
public final class DocumentSplitter {

  private DocumentSplitter() {}

  public static ParsedDocument splitAndParse(String source) {
    SectionSplit split = SectionParser.split(source);
    if (source == null) {
      return new ParsedDocument(Map.of(), Map.of(), split, List.copyOf(split.issues));
    }

    // Front matter YAML
    Map<String, Object> frontMatter = Map.of();
    List<Issue> allIssues = new java.util.ArrayList<>(split.issues);
    if (split.frontMatterText != null && !split.frontMatterText.isBlank()) {
      try {
        Object v = SafeYaml.load(split.frontMatterText);
        if (v instanceof Map<?, ?> m) {
          frontMatter = new java.util.LinkedHashMap<>();
          for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof String k) {
              frontMatter.put(k, e.getValue());
            }
          }
        } else {
          allIssues.add(Issue.structural("FRONTMATTER_TYPE",
              "front matter must be a mapping", "front_matter"));
        }
      } catch (SafeYamlException ex) {
        allIssues.add(Issue.structural(ex.code(), ex.getMessage(), "front_matter"));
      }
    }

    // Machine sections
    java.util.LinkedHashMap<String, Object> machineValues = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, String> e : split.machineSectionTexts.entrySet()) {
      String name = e.getKey();
      try {
        Object v = SafeYaml.load(e.getValue());
        machineValues.put(name, v);
      } catch (SafeYamlException ex) {
        allIssues.add(Issue.structural(ex.code(), ex.getMessage(),
            "section." + SectionParser.slug(name)));
      }
    }

    return new ParsedDocument(frontMatter, machineValues, split, allIssues);
  }
}
