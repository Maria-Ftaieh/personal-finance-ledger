package dev.ledger.app.web;

import dev.ledger.app.config.DemoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses state-changing requests when the deployment is a public demo.
 *
 * <p>Enforced here, at the edge, rather than as a check inside each service. One rule over the HTTP
 * method covers every endpoint including ones added later, which a scattering of assertions would
 * not — a new controller method would simply be unprotected, and nobody would notice until someone
 * used it.
 *
 * <p>Uploading is the exception, and only because it is the single most interesting thing to try:
 * the file is parsed and the outcome reported, but nothing is written. That is enforced separately,
 * in the import service, because "parse but do not persist" is a decision about behaviour rather
 * than about access.
 *
 * <p>This is not authentication. It is the observation that a demo has no business accepting writes
 * at all, which makes the absence of authentication harmless.
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** The one write-shaped request that is allowed through, and then refuses to persist. */
  private static final String UPLOAD_PATH = "/api/statements";

  private final DemoProperties demo;

  public DemoReadOnlyFilter(DemoProperties demo) {
    this.demo = demo;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !demo.readOnly();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    boolean writing = WRITE_METHODS.contains(request.getMethod());
    boolean uploading =
        "POST".equals(request.getMethod()) && UPLOAD_PATH.equals(request.getRequestURI());

    if (writing && !uploading) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write(
              """
              {"status":403,"error":"Forbidden",\
              "message":"This is a public demonstration and is read only. \
              Run it yourself to change anything: https://github.com/Maria-Ftaieh/personal-finance-ledger"}\
              """);
      return;
    }
    chain.doFilter(request, response);
  }
}
