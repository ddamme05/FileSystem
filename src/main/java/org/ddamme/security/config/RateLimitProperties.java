package org.ddamme.security.config;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "security.ratelimit")
public class RateLimitProperties {
    private Map<String,Integer> perMinute;             // e.g. upload=10, download=120
    private String message = "Too many requests";
    private boolean sendRetryAfter = true;
    private Duration retryAfter = Duration.ofSeconds(30);
    private Set<String> exemptPrincipals = Set.of();
}
