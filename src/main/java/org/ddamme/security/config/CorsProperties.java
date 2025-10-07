package org.ddamme.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security.cors")
public class CorsProperties {
    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
    private List<String> allowedMethods =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
    private List<String> allowedHeaders =
            List.of("Authorization", "Content-Type", "X-Request-ID", "Accept");
    private List<String> exposedHeaders = List.of("X-Request-ID", "Content-Disposition", "Location");
    private boolean allowCredentials = true;
    private Duration maxAge = Duration.ofMinutes(30);
}
