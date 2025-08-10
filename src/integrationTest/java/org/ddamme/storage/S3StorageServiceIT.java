package org.ddamme.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
@Import(S3StorageServiceIT.LocalStackS3TestConfig.class)
class S3StorageServiceIT {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.6");

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void registerS3Properties(DynamicPropertyRegistry registry) {
        registry.add("aws.s3.bucket-name", () -> "it-bucket");
    }

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @BeforeEach
    void setupBucket() {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }

    @Test
    @DisplayName("S3 put -> presign -> get -> delete works against LocalStack")
    void s3Lifecycle_works() {
        String key = "it-" + UUID.randomUUID();

        s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).contentType("text/plain").build(),
                RequestBody.fromString("hello"));

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                        .build()
        );
        assertThat(presigned.url().toString()).contains(key);

        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    @Configuration
    static class LocalStackS3TestConfig {
        @Bean
        @Primary
        S3Client s3ClientOverride() {
            return S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                    .build();
        }

        @Bean
        @Primary
        S3Presigner s3PresignerOverride() {
            return S3Presigner.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                    .build();
        }
    }
}


