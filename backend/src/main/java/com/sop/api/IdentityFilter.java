package com.sop.api;

import com.sop.config.DemoIdentities;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Enforces the demo identity on every /api/v1 request (DES-007, FR-001).
 * Missing/blank/unknown `X-Demo-User` → 401. The accepted value is stored as a
 * request attribute for role checks (403).
 */
@Component
public class IdentityFilter implements Filter {

  public static final String HEADER = "X-Demo-User";
  public static final String ATTR_AUTHORITY = "sop.identity";

  private final DemoIdentities identities;

  public IdentityFilter(DemoIdentities identities) {
    this.identities = identities;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if (!(req instanceof HttpServletRequest httpReq) || !(res instanceof HttpServletResponse httpRes)) {
      chain.doFilter(req, res);
      return;
    }
    String user = httpReq.getHeader(HEADER);
    if (user == null || user.isBlank()) {
      writeUnauthorized(httpRes, "missing identity");
      return;
    }
    String normalized = user.trim();
    Authority authority = resolve(normalized);
    if (authority == null) {
      writeUnauthorized(httpRes, "unknown identity");
      return;
    }
    httpReq.setAttribute(ATTR_AUTHORITY, authority);
    chain.doFilter(httpReq, res);
  }

  private Authority resolve(String normalized) {
    if (identities.author().equals(normalized)) return Authority.AUTHOR;
    if (identities.consumer().equals(normalized)) return Authority.CONSUMER;
    return null;
  }

  private void writeUnauthorized(HttpServletResponse res, String reason) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String body = "{\"code\":\"MISSING_IDENTITY\",\"message\":" + json(reason)
        + ",\"issues\":[]}";
    res.getWriter().write(body);
  }

  private static String json(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') b.append('\\');
      b.append(c);
    }
    return b.append('"').toString();
  }

  public enum Authority { AUTHOR("author"), CONSUMER("consumer");
    public final String name;
    Authority(String name) { this.name = name; }
  }
}
