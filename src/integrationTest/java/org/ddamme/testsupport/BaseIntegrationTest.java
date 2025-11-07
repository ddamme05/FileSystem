package org.ddamme.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("integrationTest")
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("file_system_it")
                    .withUsername("user")
                    .withPassword("password")
                    .withReuse(Boolean.parseBoolean(System.getenv().getOrDefault("TC_REUSE", "false")));

    private static final String HIKARI_MAX_LIFETIME_FOR_TESTS = "10000";

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "security.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
        registry.add("aws.s3.bucket-name", () -> "test-bucket");
        registry.add("management.health.db.enabled", () -> "false");
        // Increased from 1 to 2: afterCommit hook with REQUIRES_NEW needs 2nd connection
        registry.add("spring.datasource.hikari.maximumPoolSize", () -> "2");
        registry.add("spring.datasource.hikari.minimumIdle", () -> "1");
        registry.add("spring.datasource.hikari.connectionTimeout", () -> "10000");
        registry.add("spring.datasource.hikari.maxLifetime", () -> HIKARI_MAX_LIFETIME_FOR_TESTS);
        registry.add("spring.jpa.open-in-view", () -> "true");
        registry.add("spring.jpa.show-sql", () -> "false");

        // AI worker configuration for integration tests
        registry.add("ai.worker.enabled", () -> "true");
        registry.add("ai.worker.core-threads", () -> "1");
        registry.add("ai.worker.max-threads", () -> "2");
        registry.add("ai.worker.queue-capacity", () -> "10");
        registry.add("ai.worker.batch-size", () -> "5");
        registry.add("ai.worker.poll-interval", () -> "5000");
        registry.add("ai.worker.reclaim-interval", () -> "60000");
        registry.add("ai.worker.stale-job-timeout", () -> "PT15M");
        registry.add("ai.worker.ocr.auto-create", () -> "true");
        registry.add("ai.worker.ocr.max-pages", () -> "50");
        registry.add("ai.worker.ocr.language", () -> "eng");
        registry.add("ai.worker.ocr.data-path", () -> "/usr/share/tesseract-ocr/5/tessdata");
    }
}
