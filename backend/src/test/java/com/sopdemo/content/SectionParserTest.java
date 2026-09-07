package com.sopdemo.content;

import static com.sopdemo.content.Fixtures.validRefundDoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TASK-003 (sections) + FR-034: section structure rules (unit level). */
class SectionParserTest {

    @Test
    void missingFrontMatterIsReported() {
        ParsedDoc doc = SectionParser.parse("## Intent (When to use)\n- a\n");
        assertThat(StructuralValidator.validate(doc))
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo(Codes.MISSING_FRONTMATTER));
    }

    @Test
    void frontMatterMustBeAMapping() {
        String src = "---\n- a\n- b\n---\n## Intent (When to use)\n- x\n";
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(StructuralValidator.validate(doc))
                .extracting(Issue::code).contains(Codes.FRONTMATTER_NOT_MAPPING);
    }

    @Test
    void missingSectionIsReported() {
        String src = validRefundDoc().replace("\n## Customer Messages\n", "\n")
                .replace("primary: \"We spotted the duplicate charge and refunded it.\"", "")
                .replace("escalation: \"A billing specialist is reviewing your charges.\"", "");
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(StructuralValidator.validate(doc))
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo(Codes.MISSING_SECTION);
                    assertThat(issue.path()).isEqualTo("section:Customer Messages");
                });
    }

    @Test
    void unknownSectionIsReported() {
        String src = validRefundDoc() + "\n## Extra Junk\n- not allowed\n";
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(StructuralValidator.validate(doc))
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo(Codes.UNKNOWN_SECTION));
    }

    @Test
    void sectionsOutOfOrderAreReported() {
        String doc = validRefundDoc();
        String intentSection = doc.substring(doc.indexOf("## Intent (When to use)"),
                doc.indexOf("## Do Not Use When"));
        String doNotSection = doc.substring(doc.indexOf("## Do Not Use When"), doc.indexOf("## Inputs Required"));
        String moved = doc.replace(intentSection + doNotSection, doNotSection + intentSection);
        ParsedDoc parsed = SectionParser.parse(moved);
        assertThat(StructuralValidator.validate(parsed))
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo(Codes.SECTION_ORDER));
    }

    @Test
    void bulletSectionRejectsNonBulletLines() {
        String src = validRefundDoc()
                .replace("- A customer reports the same charge appeared twice for one order",
                        "This is not a bullet line");
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(SectionParser.parse(src).issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo(Codes.INVALID_BULLET_SECTION);
                    assertThat(issue.path()).isEqualTo("section:Intent (When to use)");
                });
    }

    @Test
    void machineSectionRejectsContentOutsideTheYamlBlock() {
        String src = validRefundDoc()
                .replace("```\n\n## Eligibility Rules\n```yaml",
                        "```\nstray prose line\n\n## Eligibility Rules\n```yaml");
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(doc.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo(Codes.INVALID_MACHINE_SECTION);
                    assertThat(issue.path()).isEqualTo("section:Inputs Required");
                });
    }

    @Test
    void machineSectionRequiresExactlyOneYamlBlock() {
        String src = validRefundDoc().replaceFirst("```yaml\\n", "");
        ParsedDoc doc = SectionParser.parse(src);
        assertThat(doc.issues())
                .anySatisfy(issue ->
                        assertThat(issue.code()).isEqualTo(Codes.INVALID_MACHINE_SECTION));
    }

    @Test
    void validDocumentParsesWithoutIssues() {
        ParsedDoc doc = SectionParser.parse(validRefundDoc());
        assertThat(doc.issues()).isEmpty();
        assertThat(doc.frontMatter()).containsEntry("sop_id", "BILL-001");
        assertThat(doc.useWhen()).hasSize(2);
        assertThat(doc.doNotUseWhen()).hasSize(2);
        assertThat(YamlSafe.asList(doc.inputs())).hasSize(2);
        assertThat(YamlSafe.asList(doc.rules())).hasSize(2);
        assertThat(YamlSafe.asList(doc.actions())).hasSize(3);
        assertThat(YamlSafe.asMapping(doc.boundaries())).containsKey("escalation");
        assertThat(YamlSafe.asMapping(doc.customerMessages())).containsKeys("primary", "escalation");
    }
}
