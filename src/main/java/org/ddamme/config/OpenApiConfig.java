package org.ddamme.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration
@OpenAPIDefinition(info = @Info(title = "File System API", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer applyGlobalSecurity() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperations().forEach(operation -> {
                    if (!path.startsWith("/api/v1/auth/")) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    }
                    if (path.startsWith("/files")) {
                        operation.setTags(List.of("Files"));
                    } else if (path.startsWith("/api/v1/auth")) {
                        operation.setTags(List.of("Auth"));
                    }
                });
            });
        };
    }
}


