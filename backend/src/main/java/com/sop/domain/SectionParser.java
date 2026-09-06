package com.sop.domain;

import com.sop.dto.Issue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a source document into front matter and ordered sections (DES-004).
 * Emits structural issues so validation can continue where possible (FR-034).
 * No authored content is executed — this only inspects text (PRN-004).
 */
public final class SectionParser {

  private static final Pattern H2 = Pattern.compile("^##(?!#)\\s+(.+?)\\s*$");
  private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+(.+?)\\s*$");
  private static final Pattern FENCE_OPEN = Pattern.compile("^```\\s*yaml\\s*$");

  private static final Set<String> PROSE = Set.of(Contract.SEC_INTENT, Contract.SEC_DO_NOT);
  private static final Set<String> MACHINE = Set.of(
      Contract.SEC_INPUTS, Contract.SEC_RULES, Contract.SEC_ACTIONS,
      Contract.SEC_BOUNDARIES, Contract.SEC_MESSAGES);
  private static final List<String> ALL_SECTIONS = List.of(
      Contract.SEC_INTENT, Contract.SEC_DO_NOT,
      Contract.SEC_INPUTS, Contract.SEC_RULES, Contract.SEC_ACTIONS,
      Contract.SEC_BOUNDARIES, Contract.SEC_MESSAGES);

  private SectionParser() {}

  public static SectionSplit split(String source) {
    List<Issue> issues = new ArrayList<>();
    if (source == null || source.isBlank()) {
      return new SectionSplit(null, new LinkedHashMap<>(), new LinkedHashMap<>(),
          List.of(Issue.structural("FRONTMATTER_MISSING",
              "source is empty", "front_matter")));
    }

    String[] lines = source.split("\n", -1);

    // ---- front matter: must open with `---` on line 1 ----
    int fmEnd = -1;
    if (!isDelim(lines[0])) {
      issues.add(Issue.structural("FRONTMATTER_MISSING",
          "source must begin with a `---` delimiter on line 1", "front_matter"));
      return new SectionSplit(null, new LinkedHashMap<>(), new LinkedHashMap<>(), issues);
    }
    for (int i = 1; i < lines.length; i++) {
      if (isDelim(lines[i])) { fmEnd = i; break; }
    }
    if (fmEnd < 0) {
      issues.add(Issue.structural("FRONTMATTER_MISSING",
          "front-matter closing `---` delimiter is missing", "front_matter"));
      return new SectionSplit(null, new LinkedHashMap<>(), new LinkedHashMap<>(), issues);
    }
    String fmText = String.join("\n", lines(1, fmEnd, lines));

    // ---- walk body: collect each `## name` + its body text, in order ----
    List<String> headingsInOrder = new ArrayList<>();
    Map<String, String> bodies = new LinkedHashMap<>();
    StringBuilder body = new StringBuilder();
    String current = null;
    boolean contentBeforeFirst = false;

    int bodyStart = fmEnd + 1;
    for (int j = bodyStart; j < lines.length; j++) {
      Matcher m = H2.matcher(lines[j]);
      if (m.matches()) {
        if (current != null) {
          bodies.put(current, body.toString());
        }
        current = m.group(1).trim();
        headingsInOrder.add(current);
        body = new StringBuilder();
      } else {
        if (current == null) {
          if (!lines[j].isBlank()) {
            if (!contentBeforeFirst) {
              contentBeforeFirst = true;
              issues.add(Issue.structural("SECTION_UNEXPECTED",
                  "content before the first section is not allowed", "body"));
            }
          }
        } else {
          body.append(lines[j]).append('\n');
        }
      }
    }
    if (current != null) {
      bodies.put(current, body.toString());
    }

    // ---- validate the section set ----
    Set<String> seen = new java.util.LinkedHashSet<>();
    for (int k = 0; k < headingsInOrder.size(); k++) {
      String name = headingsInOrder.get(k);
      if (!PROSE.contains(name) && !MACHINE.contains(name)) {
        issues.add(Issue.structural("SECTION_UNEXPECTED",
            "heading `## " + name + "` is not allowed", "section." + slug(name)));
      }
      if (!seen.add(name)) {
        issues.add(Issue.structural("SECTION_DUPLICATED",
            "section `## " + name + "` appears more than once", "section." + slug(name)));
      }
    }
    for (String name : ALL_SECTIONS) {
      if (!seen.contains(name)) {
        issues.add(Issue.structural("SECTION_MISSING",
            "required section `## " + name + "` is missing", "section." + slug(name)));
      }
    }

    // ---- classify: prose bullets vs machine single-yaml-block ----
    LinkedHashMap<String, String> machineTexts = new LinkedHashMap<>();
    LinkedHashMap<String, List<String>> proseLists = new LinkedHashMap<>();

    for (String name : ALL_SECTIONS) {
      String raw = bodies.get(name);
      if (raw == null) continue; // missing already reported
      List<String> sectionLines = trimBlankLines(raw);
      if (PROSE.contains(name)) {
        List<String> bullets = new ArrayList<>();
        for (String line : sectionLines) {
          Matcher bm = BULLET.matcher(line);
          if (bm.matches()) {
            bullets.add(bm.group(1).trim());
          } else {
            issues.add(Issue.structural("SECTION_SHAPE",
                "prose section `## " + name + "` contains a non-bullet line",
                "section." + slug(name)));
          }
        }
        if (!bullets.isEmpty()) proseLists.put(name, bullets);
      } else if (MACHINE.contains(name)) {
        String block = extractSingleYamlBlock(sectionLines, name, issues);
        if (block != null) machineTexts.put(name, block);
      }
    }

    return new SectionSplit(fmText, machineTexts, proseLists, issues);
  }

  private static List<String> lines(int from, int toExclusive, String[] all) {
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < toExclusive; i++) sb.append(all[i]).append('\n');
    String[] out = sb.toString().split("\n", -1);
    return java.util.Arrays.asList(out);
  }

  private static List<String> trimBlankLines(String raw) {
    String[] parts = raw.split("\n", -1);
    List<String> out = new ArrayList<>();
    for (String p : parts) out.add(p);
    // drop a single trailing empty caused by our join('\n')
    while (out.size() > 1 && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
    return out;
  }

  /**
   * A machine section must contain exactly one fenced `yaml` block and no other
   * nonblank content outside it (DES-004, spec §1).
   */
  private static String extractSingleYamlBlock(List<String> sectionLines, String sectionName,
                                               List<Issue> issues) {
    String path = "section." + slug(sectionName);
    List<String> yamlLines = new ArrayList<>();
    boolean inBlock = false;
    int openCount = 0;
    boolean contentOutside = false;
    boolean neverClosed = false;

    for (String line : sectionLines) {
      String t = line.trim();
      if (!inBlock) {
        if (FENCE_OPEN.matcher(t).matches()) {
          inBlock = true;
          openCount++;
        } else if (!t.isBlank()) {
          contentOutside = true;
        }
      } else {
        if (t.equals("```")) {
          inBlock = false;
        } else {
          yamlLines.add(line);
        }
      }
    }

    if (openCount > 1) {
      issues.add(Issue.structural("SECTION_SHAPE",
          "machine section `## " + sectionName + "` has more than one yaml block", path));
      return null;
    }
    if (openCount == 0) {
      issues.add(Issue.structural("SECTION_SHAPE",
          "machine section `## " + sectionName + "` has no yaml block", path));
      return null;
    }
    if (contentOutside) {
      issues.add(Issue.structural("SECTION_SHAPE",
          "machine section `## " + sectionName + "` has non-YAML-block content", path));
      return null;
    }
    if (inBlock) {
      issues.add(Issue.structural("SECTION_SHAPE",
          "machine section `## " + sectionName + "` yaml block is not closed", path));
      return null;
    }
    if (yamlLines.isEmpty()) {
      issues.add(Issue.structural("SECTION_SHAPE",
          "machine section `## " + sectionName + "` yaml block is empty", path));
      return null;
    }
    return String.join("\n", yamlLines);
  }

  private static boolean isDelim(String line) {
    String t = line.trim();
    return "---".equals(t);
  }

  public static String slug(String name) {
    StringBuilder b = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (Character.isLetterOrDigit(c)) {
        b.append(Character.toLowerCase(c));
      } else if (b.length() > 0 && b.charAt(b.length() - 1) != '_') {
        b.append('_');
      }
    }
    return b.toString();
  }
}
