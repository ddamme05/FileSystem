package org.ddamme.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@TestConfiguration
public class LocalStackS3TestConfig {

    @Container
    public static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.6"))
                    .withServices(LocalStackContainer.Service.S3)
                    .withReuse(Boolean.parseBoolean(System.getenv().getOrDefault("TC_REUSE", "false")));

    @Bean
    public LocalStackContainer localstackContainerBean() {
        if (!LOCALSTACK.isRunning()) {
            LOCALSTACK.start();
        }
        return LOCALSTACK;
    }

    @Bean
    @Primary
    public S3Client s3ClientOverride(LocalStackContainer container) {
        return S3Client.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(container.getRegion()))
                .endpointOverride(
                        URI.create(container.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                .build();
    }

    @Bean
    @Primary
    public S3Presigner s3PresignerOverride(LocalStackContainer container) {
        return S3Presigner.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(container.getRegion()))
                .endpointOverride(
                        URI.create(container.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                .build();
    }
}
