package com.sopdemo.web.identity;

import com.sopdemo.config.DemoProperties;
import com.sopdemo.web.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identity resolution (ARC-002, FR-001). Applied to {@code /api/v1/*} by the registration
 * bean. Missing or unknown identity → 401 (written via {@link ApiErrors} so the filter-scope
 * failure carries the same envelope as controller-scope errors). Author-only enforcement is
 * per-endpoint (see {@link AuthContext#requireAuthor(HttpServletRequest)}) via 403.
 */
public class IdentityFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Demo-User";

    private final DemoProperties props;
    private final ApiErrors errors;

    public IdentityFilter(DemoProperties props, ApiErrors errors) {
        this.props = props;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        List<String> authors = props.identities() != null ? props.identities().authors() : List.of();
        List<String> consumers = props.identities() != null ? props.identities().consumers() : List.of();
        String user = req.getHeader(HEADER);
        if (user == null || !(authors.contains(user) || consumers.contains(user))) {
            errors.writeHttp(
                    res,
                    HttpStatus.UNAUTHORIZED.value(),
                    "UNAUTHORIZED",
                    "a known demo identity is required via the " + HEADER + " header",
                    null);
            return;
        }
        req.setAttribute(AuthContext.KEY, new AuthenticatedUser(user, authors.contains(user)));
        chain.doFilter(req, res);
    }
}
