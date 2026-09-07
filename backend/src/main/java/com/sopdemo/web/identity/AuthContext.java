package com.sopdemo.web.identity;

import com.sopdemo.config.DemoProperties;
import com.sopdemo.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** Read-only access to the authenticated identity for the current request. */
public final class AuthContext {
    public static final String KEY = "sopdemo.authenticatedUser";
    private AuthContext() {}

    public static AuthenticatedUser current(HttpServletRequest req) {
        Object o = req.getAttribute(KEY);
        if (o instanceof AuthenticatedUser au) {
            return au;
        }
        throw new IllegalStateException("identity context missing; IdentityFilter must run first");
    }

    public static void requireAuthor(HttpServletRequest req) {
        AuthenticatedUser au = current(req);
        if (!au.author()) {
            throw ApiException.forbidden("this operation requires the author identity");
        }
    }
}
