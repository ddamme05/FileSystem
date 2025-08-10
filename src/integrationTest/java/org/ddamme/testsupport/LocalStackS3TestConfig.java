package org.ddamme.testsupport;

import java.net.URI;
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

@TestConfiguration
public class LocalStackS3TestConfig {

    @Container
    public static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.6")
    ).withServices(LocalStackContainer.Service.S3);

    static {
        LOCALSTACK.start();
    }

    @Bean
    @Primary
    public S3Client s3ClientOverride() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                .build();
    }

    @Bean
    @Primary
    public S3Presigner s3PresignerOverride() {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                .build();
    }
}


