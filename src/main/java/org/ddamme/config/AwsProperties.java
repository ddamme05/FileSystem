package org.ddamme.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    @NotBlank
    private String region;
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        @NotBlank
        private String bucketName;

        @Min(1)
        @Max(7 * 24 * 60) // S3 presign max is 7 days (10080 minutes)
        private int presignTtlMinutes = 5; // default 5 minutes

        /**
         * Server-side encryption header sent on upload (PutObject).
         * "AES256" matches AWS S3 and the AWS IAM policy that REQUIRES
         * s3:x-amz-server-side-encryption=AES256 on PutObject.
         * Leave blank/empty to send no SSE header — required for DigitalOcean
         * Spaces, which does not support SSE-S3.
         */
        private String serverSideEncryption = "AES256";
    }
}
