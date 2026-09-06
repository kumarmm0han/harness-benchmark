package com.sop.domain;

import com.sop.dto.Issue;

import java.util.List;
import java.util.Map;

/** A fully-split document: front matter value, machine-section value trees, prose lists,
 *  and any structural issues raised so far. */
public final class ParsedDocument {
  public final Map<String, Object> frontMatter;   // name -> value
  public final Map<String, Object> machineValues; // section name -> parsed value
  public final SectionSplit split;                // the split result (for traceability + tests)
  public final List<Issue> structuralIssues;      // structural issues (all stages so far)

  public ParsedDocument(Map<String, Object> frontMatter,
                        Map<String, Object> machineValues,
                        SectionSplit split,
                        List<Issue> structuralIssues) {
    this.frontMatter = frontMatter;
    this.machineValues = machineValues;
    this.split = split;
    this.structuralIssues = structuralIssues;
  }
}
