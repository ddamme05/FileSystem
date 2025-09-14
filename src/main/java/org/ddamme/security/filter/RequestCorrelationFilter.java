package org.ddamme.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  public static final String HEADER_REQUEST_ID = "X-Request-ID";
  public static final String MDC_REQUEST_ID = "request_id";
  public static final String MDC_TRACE_ID = "trace_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String incomingRequestId = request.getHeader(HEADER_REQUEST_ID);
    String requestId =
        Optional.ofNullable(incomingRequestId)
            .filter(s -> !s.isBlank())
            .orElse(UUID.randomUUID().toString());
    String traceId = UUID.randomUUID().toString();

    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_TRACE_ID, traceId);
    response.setHeader(HEADER_REQUEST_ID, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
      MDC.remove(MDC_TRACE_ID);
    }
  }
}
