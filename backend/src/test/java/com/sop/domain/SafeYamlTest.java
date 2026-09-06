package com.sop.domain;

import com.sop.dto.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-004 — SafeYAML (DES-003, FR-020, NFR-020).
 *
 * <p>Coverage:
 * <ul>
 *   <li>scalar alias rejected (hostile YAML, NFR-041)</li>
 *   <li>list alias rejected</li>
 *   <li>`*` inside a string is NOT rejected</li>
 *   <li>22-level nested list rejected; 20-level accepted</li>
 *   <li>`.inf` / `.nan` / `1e400` rejected (non-finite)</li>
 *   <li>custom tag rejected</li>
 *   <li>duplicate key rejected</li>
 *   <li>`BigInteger` accepted (finite integer)</li>
 *   <li>nested `map &gt; list &gt; map` accepted</li>
 *   <li>source &gt; 64 KiB rejected</li>
 *   <li>empty rejected</li>
 * </ul>
 */
class SafeYamlTest {

  private static Object load(String yaml) {
    return SafeYaml.load(yaml);
  }

  @Test
  @DisplayName("a scalar alias is rejected")
  void scalarAliasRejected() {
    assertThatThrownBy(() -> load("x: &a hello\ny: *a"))
        .isInstanceOf(SafeYamlException.class)
        .hasMessageContaining("alias");
  }

  @Test
  @DisplayName("a list alias is rejected")
  void listAliasRejected() {
    assertThatThrownBy(() -> load("x: &a [1,2]\ny: *a"))
        .isInstanceOf(SafeYamlException.class)
        .hasMessageContaining("alias");
  }

  @Test
  @DisplayName("a `*` inside a string literal is NOT an alias")
  void starInStringIsAllowed() {
    Object v = load("x: \"a * b\"\ny: 2");
    assertThat(v).isInstanceOf(java.util.Map.class);
  }

  @Test
  @DisplayName("exactly 20 total collection levels is accepted (root map is level 1 + 19 nested lists)")
  void depth20Accepted() {
    Object v = load(depthList(19));
    assertThat(v).isInstanceOf(java.util.Map.class);
  }

  @Test
  @DisplayName("21 total collection levels is rejected (root map level 1 + 20 nested lists)")
  void depth21Rejected() {
    assertThatThrownBy(() -> load(depthList(20)))
        .isInstanceOf(SafeYamlException.class)
        .satisfies(e -> assertThat(e.getMessage()).contains("nesting"));
  }

  @Test
  @DisplayName(".inf is rejected")
  void positiveInfinityRejected() {
    assertThatThrownBy(() -> load("a: .inf")).isInstanceOf(SafeYamlException.class);
  }

  @Test
  @DisplayName(".nan is rejected")
  void nanRejected() {
    assertThatThrownBy(() -> load("a: .nan")).isInstanceOf(SafeYamlException.class);
  }

  @Test
  @DisplayName("1e400 (overflow to Infinity) is rejected")
  void overflowRejected() {
    assertThatThrownBy(() -> load("a: 1e400")).isInstanceOf(SafeYamlException.class);
  }

  @Test
  @DisplayName("a custom tag is rejected")
  void customTagRejected() {
    assertThatThrownBy(() -> load("k: !!evil 1")).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("a duplicate mapping key is rejected")
  void duplicateKeyRejected() {
    assertThatThrownBy(() -> load("a: 1\na: 2")).isInstanceOf(runtime());
  }

  @Test
  @DisplayName("BigInteger (arbitrary-size integer) is accepted")
  void bigIntegerAccepted() {
    Object v = load("a: 99999999999999999999");
    assertThat(v).isInstanceOf(java.util.Map.class);
  }

  @Test
  @DisplayName("a nested map>list>map is accepted")
  void nestedShapesAccepted() {
    Object v = load("a:\n  - b: 1\n  - c: 2");
    assertThat(v).isInstanceOf(java.util.Map.class);
  }

  @Test
  @DisplayName("a source above 64 KiB is rejected")
  void tooBigRejected() {
    StringBuilder s = new StringBuilder("a: \"");
    for (int i = 0; i < 70_000; i++) s.append('x');
    s.append('"');
    assertThatThrownBy(() -> load(s.toString()))
        .isInstanceOf(SafeYamlException.class)
        .satisfies(e -> assertThat(e.getMessage()).contains("65536"));
  }

  @Test
  @DisplayName("an empty document is rejected")
  void emptyRejected() {
    assertThatThrownBy(() -> load("")).isInstanceOf(SafeYamlException.class);
  }

  @Test
  @DisplayName("a blank document is rejected")
  void blankRejected() {
    assertThatThrownBy(() -> load("   ")).isInstanceOf(SafeYamlException.class);
  }

  // ---- helpers ----

  private static String depthList(int depth) {
    StringBuilder s = new StringBuilder("a:");
    for (int i = 0; i < depth; i++) s.append(" [");
    s.append("1");
    for (int i = 0; i < depth; i++) s.append("]");
    return s.toString();
  }

  private static Class<? extends Throwable> runtime() {
    return RuntimeException.class;
  }
}
