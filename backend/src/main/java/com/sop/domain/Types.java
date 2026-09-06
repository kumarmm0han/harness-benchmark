package com.sop.domain;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Type/number helpers for the validators (DES-005 / DES-006). */
public final class Types {
  private Types() {}

  public static boolean isNumber(Object v) {
    return v instanceof Number
        || v instanceof BigInteger
        || v instanceof BigDecimal;
  }

  public static boolean isFiniteNumber(Object v) {
    if (!isNumber(v)) return false;
    if (v instanceof Double d) return !Double.isNaN(d) && !Double.isInfinite(d);
    if (v instanceof Float f) return !Float.isNaN(f) && !Float.isInfinite(f);
    // integer types are always finite
    return true;
  }

  public static boolean isPositiveFiniteNumber(Object v) {
    if (!isFiniteNumber(v)) return false;
    try {
      double d = toDouble(v);
      return d > 0.0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  public static double toDouble(Object v) {
    if (v instanceof Double d) return d;
    if (v instanceof Float f) return f;
    if (v instanceof Byte b) return b;
    if (v instanceof Short s) return s;
    if (v instanceof Integer i) return i;
    if (v instanceof Long l) return l;
    if (v instanceof BigInteger bi) return bi.doubleValue();
    if (v instanceof BigDecimal bd) return bd.doubleValue();
    if (v instanceof Number n) return n.doubleValue();
    throw new IllegalArgumentException("not a number: " + v);
  }

  /** Numeric equality for amount comparisons (handles int/long/double/big*). */
  public static boolean sameNumber(Object a, Object b) {
    if (!isNumber(a) || !isNumber(b)) return false;
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    return da.compareTo(db) == 0;
  }

  private static BigDecimal toBigDecimal(Object v) {
    if (v instanceof BigDecimal bd) return bd;
    if (v instanceof BigInteger bi) return new BigDecimal(bi);
    if (v instanceof Double d) return BigDecimal.valueOf(d);
    if (v instanceof Float f) return BigDecimal.valueOf(f);
    if (v instanceof Byte b) return BigDecimal.valueOf(b);
    if (v instanceof Short s) return BigDecimal.valueOf(s);
    if (v instanceof Integer i) return BigDecimal.valueOf(i);
    if (v instanceof Long l) return BigDecimal.valueOf(l);
    return new BigDecimal(v.toString());
  }

  public static boolean isBlank(String s) { return s == null || s.isBlank(); }

  public static boolean isNonBlankString(Object v) {
    return v instanceof String s && !s.isBlank();
  }
}
