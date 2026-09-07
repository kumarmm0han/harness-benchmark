package com.sopdemo.domain.parse;

import static com.sopdemo.domain.issue.IssueCode.ALIASES_REJECTED;
import static com.sopdemo.domain.issue.IssueCode.CUSTOM_TAG_REJECTED;
import static com.sopdemo.domain.issue.IssueCode.DUPLICATE_KEY;
import static com.sopdemo.domain.issue.IssueCode.INVALID_YAML;
import static com.sopdemo.domain.issue.IssueCode.NESTING_TOO_DEEP;
import static com.sopdemo.domain.issue.IssueCode.NON_FINITE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sopdemo.domain.parse.SafeYaml.ParseLimits;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeYamlTest {

    private static final ParseLimits L = ParseLimits.DEFAULT;

    private static YamlFailure parseFailing(String yaml) {
        try {
            SafeYaml.parse(yaml, L);
        } catch (YamlFailure e) {
            return e;
        }
        throw new AssertionError("expected YamlFailure");
    }

    @Test
    void parsesListWithScalars() {
        Object v = SafeYaml.parse("- name: x\n  type: number", L);
        assertThat(v).isInstanceOf(List.class);
        Map<Object, Object> first = (Map<Object, Object>) ((List<?>) v).get(0);
        assertThat(first.get("name")).isEqualTo("x");
        assertThat(first.get("type")).isEqualTo("number");
    }

    @Test
    void parsesMapPreservingTypes() {
        Map<Object, Object> v = (Map<Object, Object>) SafeYaml.parse("a: true\nn: 200\nd: 1.5\ns: hi", L);
        assertThat(v.get("a")).isEqualTo(Boolean.TRUE);
        assertThat((Number) v.get("n")).isEqualTo(200L);
        assertThat((Number) v.get("d")).isEqualTo(1.5);
        assertThat(v.get("s")).isEqualTo("hi");
    }

    @Test
    void rejectsAliases() {
        assertThat(parseFailing("a: &x 1\nb: *x").code()).isEqualTo(ALIASES_REJECTED);
    }

    @Test
    void rejectsCustomOrJavaTags() {
        // Any custom/Java tag must be rejected; the exact code is either our tag guard or
        // SnakeYAML's composer. Both are structural rejections (FR-020/NFR-020).
        assertThat(parseFailing("a: !!java.lang.Runtime 1").code())
                .isIn(CUSTOM_TAG_REJECTED, INVALID_YAML);
    }

    @Test
    void rejectsUserDefinedTagToNodeSpecificTag() {
        // A user-defined tag that composes to a node should hit our CUSTOM_TAG_REJECTED guard.
        try {
            SafeYaml.parse("a: !custom x", L);
        } catch (YamlFailure e) {
            assertThat(e.code()).isIn(CUSTOM_TAG_REJECTED, INVALID_YAML);
            return;
        }
        throw new AssertionError("expected rejection of !custom tag");
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThat(parseFailing("a: 1\na: 2").code()).isEqualTo(DUPLICATE_KEY);
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThat(parseFailing("a: .inf").code()).isEqualTo(NON_FINITE_NUMBER);
        assertThat(parseFailing("a: .nan").code()).isEqualTo(NON_FINITE_NUMBER);
    }

    @Test
    void rejectsExcessiveNesting() {
        // 25 nested collection levels > the limit of 20. Rejected with NESTING_TOO_DEEP, or
        // INVALID_YAML if SnakeYAML's own composer depth guard trips first — both are rejections.
        String deep = nested(25);
        assertThat(parseFailing(deep).code()).isIn(NESTING_TOO_DEEP, INVALID_YAML);
    }

    private static String nested(int levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            sb.append('[');
        }
        sb.append("1");
        for (int i = 0; i < levels; i++) {
            sb.append(']');
        }
        return sb.toString();
    }

    @Test
    void rejectsMalformedYaml() {
        assertThatThrownBy(() -> SafeYaml.parse("a:\n - b: 1\n  c: 2\n - d: 3", L))
                .isInstanceOf(YamlFailure.class)
                .satisfies(e -> assertThat(((YamlFailure) e).code()).isIn(INVALID_YAML));
    }

    @Test
    void acceptsNestingAtLimit() {
        // Exactly the 20-level limit is allowed (root collection = level 1).
        assertThat(SafeYaml.parse(nested(20), L)).isInstanceOf(List.class);
    }
}
