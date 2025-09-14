package org.ddamme.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!it-localstack")
@RequiredArgsConstructor
public class AwsConfig {

  private final AwsProperties awsProperties;

  private Region region() {
    return Region.of(awsProperties.getRegion());
  }

  @Bean
  public S3Client s3Client() {
    return S3Client.builder().region(region()).build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return S3Presigner.builder().region(region()).build();
  }
}
