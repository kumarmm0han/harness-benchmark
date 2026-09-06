package com.sop.domain;

import java.util.Collection;
import java.util.Map;

/**
 * Walks a YAML value tree to enforce the depth and finite-number limits
 * (DES-003, spec §1 "YAML nesting to 20 collection levels, counting a root
 * mapping/list as level 1").
 *
 * <p>Depth of a node: a scalar has depth 0; a collection has depth
 * 1 + max(depth of its elements). We reject any node whose depth exceeds 20.
 */
final class DepthAndFiniteChecker {

  private DepthAndFiniteChecker() {}

  static void check(Object node, int maxDepth) {
    int depth = depthOf(node);
    if (depth > maxDepth) {
      throw new SafeYamlException(SafeYamlException.NESTING_TOO_DEEP,
          "YAML collection nesting exceeds level " + maxDepth);
    }
    checkFinite(node);
  }

  private static int depthOf(Object node) {
    if (node instanceof Map<?, ?> m) {
      int d = 1;
      for (Object v : m.values()) {
        d = Math.max(d, 1 + depthOf(v));
      }
      return d;
    }
    if (node instanceof Collection<?> c) {
      int d = 1;
      for (Object v : c) {
        d = Math.max(d, 1 + depthOf(v));
      }
      return d;
    }
    return 0; // scalar
  }

  private static void checkFinite(Object node) {
    if (node instanceof Map<?, ?> m) {
      for (Object v : m.values()) checkFinite(v);
      return;
    }
    if (node instanceof Collection<?> c) {
      for (Object v : c) checkFinite(v);
      return;
    }
    if (node instanceof Double d && (Double.isNaN(d) || Double.isInfinite(d))) {
      throw new SafeYamlException(SafeYamlException.NON_FINITE_NUMBER,
          "non-finite numbers are not allowed");
    }
    if (node instanceof Float f && (Float.isNaN(f) || Float.isInfinite(f))) {
      throw new SafeYamlException(SafeYamlException.NON_FINITE_NUMBER,
          "non-finite numbers are not allowed");
    }
    // Integer, Long, Double (finite), Boolean, String, BigInteger are all legal.
  }
}
