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

  @NotBlank private String region;
  private S3 s3 = new S3();

  @Data
  public static class S3 {
    @NotBlank private String bucketName;

    @Min(1)
    @Max(7 * 24 * 60) // S3 presign max is 7 days (10080 minutes)
    private int presignTtlMinutes = 5; // default 5 minutes
  }
}
