package org.ddamme.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Security guard to prevent production deployment with default JWT secrets. Fails fast at startup
 * if default/weak secrets are detected in production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecurityGuard {

  private final Environment environment;

  // Default JWT secret from application.yml (base64 encoded)
  private static final String DEFAULT_JWT_SECRET = "IkZTI0VwjSvZuMo99cXAx9xzeJhKHLyJODC5PoHsjO4=";

  @PostConstruct
  void validateJwtSecret() {
    if (environment.matchesProfiles("prod")) {
      String jwtSecret = environment.getProperty("security.jwt.secret", "");

      if (jwtSecret.isBlank()) {
        throw new IllegalStateException(
            "SECURITY VIOLATION: JWT secret is blank in production. "
                + "Set SECURITY_JWT_SECRET environment variable with a secure base64-encoded secret.");
      }

      if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
        throw new IllegalStateException(
            "SECURITY VIOLATION: Refusing to start with default JWT secret in production. "
                + "Generate a secure secret: head -c 32 /dev/urandom | base64");
      }

      // Additional validation for weak secrets
      if (jwtSecret.length() < 32) {
        log.warn(
            "JWT secret appears short for production use. Consider using a longer base64-encoded secret.");
      }

      log.info("JWT secret validation passed for production environment");
    }
  }
}
