package org.ddamme.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        final String endpoint = System.getenv("AWS_ENDPOINT_URL_S3");
        final AwsCredentialsProvider creds = resolveCredentialsProvider(endpoint);

        S3ClientBuilder builder = S3Client.builder()
                .region(region())
                .credentialsProvider(creds);

        if (isLocalStack(endpoint)) {
            log.info("Using LocalStack S3 endpoint: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(
                            S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build()
                    );
        } else {
            log.info("Using DefaultCredentialsProvider chain for AWS");
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        final String endpoint = System.getenv("AWS_ENDPOINT_URL_S3");
        final AwsCredentialsProvider creds = resolveCredentialsProvider(endpoint);

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(region())
                .credentialsProvider(creds);

        if (isLocalStack(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    private Region region() {
        return Region.of(awsProperties.getRegion());
    }

    /**
     * Resolves the appropriate credential provider.
     * For LocalStack, it uses the credentials set in the environment (e.g., from docker-compose).
     * For real AWS, it uses the default chain (env → ~/.aws/SSO → IAM role, etc.).
     */
    private AwsCredentialsProvider resolveCredentialsProvider(String endpoint) {
        if (isLocalStack(endpoint)) {
            return EnvironmentVariableCredentialsProvider.create();
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private boolean isLocalStack(String endpoint) {
        return endpoint != null && !endpoint.isBlank();
    }
}