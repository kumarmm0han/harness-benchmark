package com.sopdemo.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TASK-003/FR-020: safe YAML loader rejections and limits (unit level). */
class YamlSafeTest {

    @Test
    void aliasesAreRejectedWithStableCodeAndReadableView() {
        YamlSafe.Loaded loaded = YamlSafe.load("a: &x 1\nb: *x", "frontmatter");
        assertThat(loaded.issues()).hasSize(1);
        assertThat(loaded.issues().get(0).code()).isEqualTo(Codes.ALIAS_OR_TAG);
        assertThat(loaded.issues().get(0).stage()).isEqualTo(Issue.STAGE_STRUCTURAL);
        assertThat(loaded.issues().get(0).message()).isEqualTo("YAML aliases are not allowed in this document.");
        assertThat(loaded.issues().get(0).path()).isEqualTo("frontmatter");
    }

    @Test
    void duplicateKeysAreRejectedWithDistinctCode() {
        YamlSafe.Loaded loaded = YamlSafe.load("a: 1\na: 2", "frontmatter");
        assertThat(loaded.issues()).hasSize(1);
        assertThat(loaded.issues().get(0).code()).isEqualTo(Codes.DUPLICATE_KEY);
    }

    @Test
    void customTagsAreRejectedAndNeverExecute() {
        YamlSafe.Loaded loaded = YamlSafe.load("!!javax.script.ScriptEngineManager []", "frontmatter");
        assertThat(loaded.issues()).hasSize(1);
        assertThat(loaded.issues().get(0).code()).isEqualTo(Codes.MALFORMED_YAML);
        assertThat(loaded.issues().get(0).path()).isEqualTo("frontmatter");
    }

    @Test
    void malformedSyntaxIsReportedAsStructuralIssue() {
        YamlSafe.Loaded loaded = YamlSafe.load("a: [1,\nb", "frontmatter");
        assertThat(loaded.issues()).hasSize(1);
        assertThat(loaded.issues().get(0).code()).isEqualTo(Codes.MALFORMED_YAML);
        assertThat(loaded.issues().get(0).message())
                .isEqualTo("The YAML block is malformed or uses unsupported tags.");
    }

    @Test
    void nonFiniteNumbersAreRejectedWithReadableMessage() {
        YamlSafe.Loaded loaded = YamlSafe.load("x: .nan\ny: .inf", "frontmatter");
        assertThat(loaded.issues()).extracting(Issue::code).contains(Codes.NON_FINITE_NUMBER);
        for (Issue issue : loaded.issues()) {
            assertThat(issue.message()).isEqualTo("Numeric values must be finite.");
            assertThat(issue.path()).isIn("frontmatter.x", "frontmatter.y");
        }
    }

    @Test
    void nestingUpToLimitIsAccepted() {
        // depth = 1 (top mapping) + number of nested lists; 1 + 19 = 20 is within the limit
        String yaml = buildNested(19);
        YamlSafe.Loaded loaded = YamlSafe.load(yaml, "frontmatter");
        assertThat(loaded.issues()).isEmpty();
    }

    @Test
    void nestingBeyondLimitIsRejectedWithoutStackOverflow() {
        // 1 (top mapping) + 20 nested lists = 21 exceeds the 20 level limit
        String yaml = buildNested(20);
        YamlSafe.Loaded loaded = YamlSafe.load(yaml, "frontmatter");
        assertThat(loaded.issues()).hasSize(1);
        assertThat(loaded.issues().get(0).code()).isEqualTo(Codes.NESTING_TOO_DEEP);
        assertThat(loaded.issues().get(0).message())
                .isEqualTo("YAML nesting exceeds the 20 level limit.");
    }

    @Test
    void safeTypesLoadWithoutExecution() {
        YamlSafe.Loaded loaded = YamlSafe.load(
                "a: 1\nb: [l1, l2]\nc: {k: v}\nd: true", "frontmatter");
        assertThat(loaded.issues()).isEmpty();
        assertThat(YamlSafe.asMapping(loaded.value())).containsKeys("a", "b", "c", "d");
    }

    private static String buildNested(int nestedLists) {
        StringBuilder sb = new StringBuilder("v: ");
        for (int i = 0; i < nestedLists; i++) {
            sb.append('[');
        }
        for (int i = 0; i < nestedLists; i++) {
            sb.append(']');
        }
        return sb.toString();
    }
}
