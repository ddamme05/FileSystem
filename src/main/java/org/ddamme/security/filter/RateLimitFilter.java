package org.ddamme.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ddamme.dto.ErrorResponse;
import org.ddamme.metrics.Metrics;
import org.ddamme.security.config.RateLimitProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties props;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  private final Cache<String, Bucket> cache =
      Caffeine.newBuilder()
          .maximumSize(50_000) // Rule of thumb: active_principals_per_30min * route_keys
          // E.g., 15k active users/IPs * 2 routes = 30k buckets
          .expireAfterAccess(Duration.ofMinutes(30))
          .build();

  // optional: toggle high-cardinality tag in non-prod only
  private static final boolean INCLUDE_PRINCIPAL_TAG =
      !"prod".equalsIgnoreCase(System.getenv().getOrDefault("DD_ENV", "dev"));

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String routeKey = matchRouteKey(request.getMethod(), request.getRequestURI());
    if (routeKey == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String principalKey = resolvePrincipalKey();

    // Check if user is exempt (if configured)
    if (principalKey != null
        && props.getExemptPrincipals() != null
        && props.getExemptPrincipals().contains(principalKey)) {
      filterChain.doFilter(request, response);
      return;
    }

    int perMinute =
        props.getPerMinute() == null ? 0 : props.getPerMinute().getOrDefault(routeKey, 0);
    if (perMinute <= 0) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = routeKey + "|" + (principalKey == null ? clientIp(request) : "u:" + principalKey);
    Bucket bucket = Objects.requireNonNull(cache.get(key, k -> newBucket(perMinute)));

    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
    } else {
      // Increment observability counter for alerting on abuse
      String principal = principalKey != null ? principalKey : clientIp(request);
      if (INCLUDE_PRINCIPAL_TAG) {
        Metrics.increment(meterRegistry, "http.ratelimit.rejects",
            "route", routeKey, "limit", String.valueOf(perMinute),
            "principal", principal);
      } else {
        Metrics.increment(meterRegistry, "http.ratelimit.rejects",
            "route", routeKey, "limit", String.valueOf(perMinute));
      }

      response.setStatus(429);
      response.setContentType("application/json");

      // Add standard rate limit headers including precise reset time
      var probe = bucket.estimateAbilityToConsume(1);
      long remaining = Math.max(0, bucket.getAvailableTokens());
      long resetSeconds = Math.max(0, probe.getNanosToWaitForRefill() / 1_000_000_000L);
      long suggested = Math.max(resetSeconds, props.getRetryAfter().toSeconds());

      response.setHeader("X-RateLimit-Limit", String.valueOf(perMinute));
      response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
      response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));
      response.setHeader("X-RateLimit-Window", "60"); // seconds

      // Only send Retry-After if enabled
      if (props.isSendRetryAfter()) {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(suggested));
      }

      // Don't write response body for HEAD requests (HTTP nicety)
      if ("HEAD".equals(request.getMethod())) {
        response.setContentLength(0);
        return;
      }

      // Use ErrorResponse for consistent timestamp formatting
      var errorResponse =
          new ErrorResponse(
              java.time.Instant.now(),
              429,
              "Too Many Requests",
              props.getMessage(),
              "uri=" + request.getRequestURI());
      response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
  }

  private Bucket newBucket(int perMinute) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(perMinute)
            .refillGreedy(perMinute, Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private String resolvePrincipalKey() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return (a != null && a.isAuthenticated() && a.getName() != null) ? a.getName() : null;
  }

  private String clientIp(HttpServletRequest req) {
    // Parse X-Forwarded-For for real client IP behind proxies
    // DEPLOYMENT NOTE: Ensure your proxy (ALB/NGINX/Cloudflare) strips incoming
    // X-Forwarded-* headers from edge and re-adds its own to prevent IP spoofing
    String xf = req.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) {
      int comma = xf.indexOf(',');
      return comma > 0 ? xf.substring(0, comma).trim() : xf.trim();
    }
    return req.getRemoteAddr();
  }

  // Only the sensitive routes we care about
  private String matchRouteKey(String method, String path) {
    boolean isUpload = "POST".equals(method) && path.equals("/api/v1/files/upload");
    boolean isDownload =
        ("GET".equals(method) || "HEAD".equals(method))
            && path.startsWith("/api/v1/files/download/");
    boolean isLogin = "POST".equals(method) && path.equals("/api/v1/auth/login");

    if (isUpload) return "upload";
    if (isDownload) return "download";
    if (isLogin) return "login";
    return null;
  }

  // (old cached counters removed)
}
