package org.ddamme.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.ddamme.testsupport.LocalStackS3TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

class S3StorageServiceIT {
    private static S3Client s3Client;
    private static S3Presigner s3Presigner;
    private static final String bucketName = "it-bucket";

    @BeforeAll
    static void initClients() {
        // Ensure LocalStack container from shared test config is running
        var localstack = LocalStackS3TestConfig.LOCALSTACK;
        Region region = Region.of(localstack.getRegion());
        URI endpoint = URI.create(localstack.getEndpointOverride(org.testcontainers.containers.localstack.LocalStackContainer.Service.S3).toString());

        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(region)
                .endpointOverride(endpoint)
                .build();

        s3Presigner = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(region)
                .endpointOverride(endpoint)
                .build();
    }

    @BeforeEach
    void setupBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (!"BucketAlreadyOwnedByYou".equals(e.awsErrorDetails().errorCode())) {
                throw e;
            }
        }
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

}


