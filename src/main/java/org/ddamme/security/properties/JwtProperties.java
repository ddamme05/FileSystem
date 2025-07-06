package org.ddamme.security.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "application.security.jwt")
@Getter
@Setter
@Validated
public class JwtProperties {

    private String secretKey;
} 