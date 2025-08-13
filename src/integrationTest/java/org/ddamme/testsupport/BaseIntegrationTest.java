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
					.withReuse(true);

	private static final String HIKARI_MAX_LIFETIME_FOR_TESTS = "10000";

	@DynamicPropertySource
	static void registerDynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("security.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1uMDEyMzQ1Njc4OWFiY2RlZg==");
		registry.add("aws.s3.bucket-name", () -> "test-bucket");
		registry.add("management.health.db.enabled", () -> "false");
		registry.add("spring.datasource.hikari.maximumPoolSize", () -> "1");
		registry.add("spring.datasource.hikari.maxLifetime", () -> HIKARI_MAX_LIFETIME_FOR_TESTS);
		registry.add("spring.jpa.open-in-view", () -> "true");
		registry.add("spring.jpa.show-sql", () -> "false");
	}
}


