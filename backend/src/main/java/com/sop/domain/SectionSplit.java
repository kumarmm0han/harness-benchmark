package com.sop.domain;

import com.sop.dto.Issue;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The result of splitting a source document into front matter and ordered
 * sections (DES-004). Machine sections hold their raw fenced-yaml text; prose
 * sections hold the list of bullet lines.
 */
public final class SectionSplit {
  public final String frontMatterText;          // text between the two `---` lines (or null)
  public final LinkedHashMap<String, String> machineSectionTexts; // name -> fenced yaml block text
  public final LinkedHashMap<String, List<String>> proseSections; // name -> bullet lines (raw)
  public final List<Issue> issues;               // structural issues found during splitting

  public SectionSplit(
      String frontMatterText,
      LinkedHashMap<String, String> machineSectionTexts,
      LinkedHashMap<String, List<String>> proseSections,
      List<Issue> issues
  ) {
    this.frontMatterText = frontMatterText;
    this.machineSectionTexts = machineSectionTexts;
    this.proseSections = proseSections;
    this.issues = issues;
  }
}
