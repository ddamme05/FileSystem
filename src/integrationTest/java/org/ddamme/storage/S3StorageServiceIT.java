package org.ddamme.storage;

import org.ddamme.testsupport.LocalStackS3TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackS3TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class S3StorageServiceIT {

    private static final String bucketName = "it-bucket";
    @Autowired
    private S3Client s3Client;
    @Autowired
    private S3Presigner s3Presigner;

    @BeforeEach
    void setupBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            if (!"BucketAlreadyOwnedByYou".equals(e.awsErrorDetails().errorCode())) {
                throw e;
            }
        }
    }

    @Test
    @DisplayName("S3 put -> presign -> get -> delete works against LocalStack")
    void s3Lifecycle_works() {
        String key = "it-" + UUID.randomUUID();

        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).contentType("text/plain").build(),
                RequestBody.fromString("hello"));

        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                                .build());
        assertThat(presigned.url().toString()).contains(key);

        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }
}
