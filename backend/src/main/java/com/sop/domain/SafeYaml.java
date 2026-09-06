package com.sop.domain;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.scanner.Scanner;
import org.yaml.snakeyaml.scanner.ScannerImpl;
import org.yaml.snakeyaml.tokens.Token;

import java.nio.charset.StandardCharsets;

/**
 * Safe YAML loading (DES-003).
 *
 * <p>Restrictions (spec §1 / NFR-020 / PRN-004):
 * <ul>
 *   <li>aliases are rejected (scalar, collection, or flow) via a token-scan —
 *       SnakeYAML 2.4 does not expose AliasNode, and scalar aliases would
 *       otherwise collapse into plain nodes and be indistinguishable.</li>
 *   <li>duplicate mapping keys are rejected by SnakeYAML when
 *       {@code setAllowDuplicateKeys(false)}. Belt-and-suspenders.</li>
 *   <li>nesting depth > 20 is rejected by a recursive walk (SnakeYAML has
 *       no built-in depth limit).</li>
 *   <li>non-finite numbers (NaN, +/-Infinity) are rejected by a walk
 *       (SnakeYAML accepts {@code .inf}, {@code .nan}, {@code 1e400}).</li>
 *   <li>custom tags are rejected by {@link SafeConstructor}; we do not add
 *       any custom type constructor.</li>
 *   <li>the source is UTF-8, <= 65,536 bytes (64 KiB).</li>
 * </ul>
 *
 * <p>Any violated restriction throws {@link SafeYamlException} carrying an
 * issue code. No authored value is ever evaluated — this class parses
 * <em>data</em> only (PRN-004).
 *
 * <p>The returned value is a plain {@code Map}/{@code List}/{@code String}/
 * {@code Integer}/{@code Long}/{@code Double}/{@code Boolean}/{@code BigInteger}
 * tree — safe for Jackson serialization.
 */
public final class SafeYaml {
  private SafeYaml() {}

  public static final int MAX_DEPTH = 20;
  public static final int SOURCE_BYTES_MAX = 65_536;

  /** Load a YAML document into a plain value tree with full safety checks. */
  public static Object load(String yamlSource) {
    if (yamlSource == null || yamlSource.isBlank()) {
      throw new SafeYamlException(SafeYamlException.EMPTY, "empty YAML document");
    }
    if (sourceBytes(yamlSource) > SOURCE_BYTES_MAX) {
      throw new SafeYamlException(SafeYamlException.SOURCE_TOO_LARGE,
          "source exceeds " + SOURCE_BYTES_MAX + " UTF-8 bytes");
    }
    if (containsAliasToken(yamlSource)) {
      throw new SafeYamlException(SafeYamlException.ALIAS,
          "YAML aliases are not allowed");
    }
    Object parsed;
    try {
      parsed = safeParse(yamlSource);
    } catch (YAMLException e) {
      throw new SafeYamlException(SafeYamlException.PARSE_FAILED,
          shortMessage(e.getMessage()));
    }
    if (parsed == null) {
      throw new SafeYamlException(SafeYamlException.EMPTY, "empty YAML document");
    }
    DepthAndFiniteChecker.check(parsed, MAX_DEPTH);
    return parsed;
  }

  private static Object safeParse(String yamlSource) {
    LoaderOptions o = new LoaderOptions();
    o.setAllowDuplicateKeys(false);
    o.setMaxAliasesForCollections(0);
    return new Yaml(new SafeConstructor(o)).load(yamlSource);
  }

  /** Token-scan the source for any AliasToken (alias rejection, DES-003). */
  private static boolean containsAliasToken(String yamlSource) {
    Scanner sc = new ScannerImpl(new StreamReader(yamlSource), new LoaderOptions());
    while (!sc.checkToken(Token.ID.StreamEnd)) {
      if (sc.checkToken(Token.ID.Alias)) {
        return true;
      }
      sc.getToken();
    }
    return false;
  }

  private static int sourceBytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8).length;
  }

  private static String shortMessage(String msg) {
    if (msg == null) return "YAML parse failure";
    int nl = msg.indexOf('\n');
    String first = nl < 0 ? msg : msg.substring(0, nl);
    first = first.replaceFirst("^while\\s+.*", "YAML parse failure");
    return first.length() > 140 ? first.substring(0, 140) : first;
  }
}
