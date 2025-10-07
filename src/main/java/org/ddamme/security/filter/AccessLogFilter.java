package org.ddamme.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS = LoggerFactory.getLogger("ACCESS");
    private static final Set<String> SKIP_PREFIXES =
            Set.of("/actuator/health", "/actuator/health/", "/actuator/info");

    private static void putMdc(String k, String v) {
        if (v != null) MDC.put(k, v);
    }

    private static String coalesce(String a, String b, String c) {
        return a != null ? a : b != null ? b : c;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {
        Instant start = Instant.now();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String path = uri + (queryString == null ? "" : "?" + queryString);

        boolean skip = SKIP_PREFIXES.stream().anyMatch(uri::startsWith);
        String method = request.getMethod();
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = request.getRemoteAddr();

        // Grab request id from MDC (set by your RequestCorrelationFilter) or the header
        String requestId =
                coalesce(MDC.get("X-Request-ID"), MDC.get("request_id"), request.getHeader("X-Request-ID"));

        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            int status = response.getStatus();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String user = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";

            // Populate MDC for logstash encoder to emit as structured fields
            putMdc("method", method);
            putMdc("path", path);
            putMdc("status", String.valueOf(status));
            putMdc("latency_ms", String.valueOf(latencyMs));
            putMdc("user", user);
            putMdc("user_agent", userAgent);
            putMdc("remote_addr", remoteAddr);
            if (requestId != null) putMdc("request_id", requestId);

            try {
                if (!skip) {
                    ACCESS.info("access");
                }
            } finally {
                // Clean up to avoid MDC bleed between threads
                MDC.remove("method");
                MDC.remove("path");
                MDC.remove("status");
                MDC.remove("latency_ms");
                MDC.remove("user");
                MDC.remove("user_agent");
                MDC.remove("remote_addr");
                MDC.remove("request_id");
            }
        }
    }
}
