package org.ddamme.security.properties;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "security.jwt")
@Validated
@Data
public class JwtProperties {
    private String secret;

    @Positive
    private long expirationMs;
}


