package org.ddamme.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ddamme.dto.ErrorResponse;
import org.ddamme.security.filter.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final AuthenticationProvider authenticationProvider;
  private final RequestCorrelationFilter requestCorrelationFilter;
  private final CacheControlFilter cacheControlFilter;
  private final RateLimitFilter rateLimitFilter;
  private final AccessLogFilter accessLogFilter;
  private final Environment environment;

  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, authException) -> {
      ErrorResponse errorResponse =
          new ErrorResponse(
              Instant.now(),
              HttpServletResponse.SC_UNAUTHORIZED,
              "Unauthorized",
              "Authentication required",
              "uri=" + request.getRequestURI());

      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setHeader("WWW-Authenticate", "Bearer");
      response.setContentType("application/json");
      response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint authenticationEntryPoint,
      ObjectMapper objectMapper)
      throws Exception {
    http.cors(withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .headers(
            headers ->
                headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .contentTypeOptions(withDefaults())
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

    // Only enable HSTS in production (HTTPS only)
    if (isProductionProfile()) {
      http.headers(
          headers ->
              headers.httpStrictTransportSecurity(
                  hsts ->
                      hsts.includeSubDomains(true)
                          .preload(true)
                          .maxAgeInSeconds(63072000))); // 2 years
    }

    http.headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp -> {
                      // API-safe CSP for production, relaxed for dev to allow Swagger UI
                      String cspPolicy =
                          isProductionProfile()
                              ? "default-src 'none'"
                              : "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:";
                      csp.policyDirectives(cspPolicy);
                    }))
        .authorizeHttpRequests(
            authorize -> {
              authorize
                  .requestMatchers(
                      "/api/v1/auth/**",
                      "/actuator/health",
                      "/actuator/health/**",
                      "/actuator/info")
                  .permitAll();

              // Disable Swagger in production
              if (!isProductionProfile()) {
                authorize
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll();
              }

              authorize.anyRequest().authenticated();
            })
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(
                        (req, res, e) -> {
                          var body =
                              new ErrorResponse(
                                  Instant.now(),
                                  403,
                                  "Forbidden",
                                  "You do not have permission to access this resource",
                                  "uri=" + req.getRequestURI());
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter().write(objectMapper.writeValueAsString(body));
                        }))
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(cacheControlFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(requestCorrelationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(accessLogFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, AccessLogFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties cp) {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(cp.getAllowedOrigins()); // exact origins
    cfg.setAllowedMethods(cp.getAllowedMethods());
    cfg.setAllowedHeaders(cp.getAllowedHeaders());
    cfg.setExposedHeaders(cp.getExposedHeaders());
    cfg.setAllowCredentials(cp.isAllowCredentials());
    cfg.setMaxAge(cp.getMaxAge());
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  private boolean isProductionProfile() {
    return environment.matchesProfiles("prod");
  }
}
