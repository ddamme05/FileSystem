package org.ddamme.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class BaseIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("file_system_it")
                    .withUsername("user")
                    .withPassword("password")
                    .withReuse(true);

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("security.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        registry.add("aws.s3.bucket-name", () -> "test-bucket");
        registry.add("spring.datasource.hikari.maxLifetime", () -> "10000");
        registry.add("spring.jpa.show-sql", () -> "false");
    }
}


