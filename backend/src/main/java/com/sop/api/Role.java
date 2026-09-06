package com.sop.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Enforces the per-endpoint role (DES-007, PRN-004: backend authority).
 * Author-only endpoints call {@link #requireAuthor} and throw 403 for consumers.
 */
public final class Role {
  private Role() {}

  public static final String ATTRIBUTE = IdentityFilter.ATTR_AUTHORITY;

  public static void requireAuthor(HttpServletRequest req) {
    Object authority = req.getAttribute(ATTRIBUTE);
    if (authority != IdentityFilter.Authority.AUTHOR) {
      throw new ApiException(ErrorCode.FORBIDDEN,
          "this operation requires the author identity");
    }
  }

  public static boolean isAuthor(HttpServletRequest req) {
    return req.getAttribute(ATTRIBUTE) == IdentityFilter.Authority.AUTHOR;
  }
}
