package com.cineagent.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with a {@code traceId}, available via MDC to every log line for that
 * request and echoed on every {@link org.springframework.http.ProblemDetail} response (see
 * {@link com.cineagent.common.error.GlobalExceptionHandler}). Lets a reviewer go from a 500
 * response straight to the matching stack trace in the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_MDC_KEY = "traceId";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = UUID.randomUUID().toString();
    MDC.put(TRACE_ID_MDC_KEY, traceId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID_MDC_KEY);
    }
  }
}
