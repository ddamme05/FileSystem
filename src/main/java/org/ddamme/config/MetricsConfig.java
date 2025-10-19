package org.ddamme.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.ddamme.database.repository.MetadataRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics configuration for capacity gauges and S3 health monitoring.
 * Keeps the metrics footprint minimal while providing essential observability.
 * <p>
 * Metrics Overview:
 * - s3.op.*: Real traffic S3 operations (put, get_presign, delete) with latency and errors
 * - dep.s3.check.*: Periodic S3 health canary (every 60s) for early dependency failure detection
 * - fs.files.total / fs.bytes.total: Capacity gauges for growth planning
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry registry;
    private final MetadataRepository metadataRepository;
    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    // Pre-registered timers to avoid re-registration on every health check
    private Timer s3HealthUpTimer;
    private Timer s3HealthDownTimer;
    private Counter s3HealthErrorCounter;

    /**
     * Capacity gauges for tracking storage growth and cost planning.
     */
    @Bean
    MeterBinder capacityMeters() {
        return r -> {
            // Total file count gauge
            Gauge.builder("fs.files.total", metadataRepository, repo -> (double) repo.count())
                    .register(r);

            // Total bytes stored gauge
            Gauge.builder("fs.bytes.total", metadataRepository, repo -> (double) repo.sumSizes())
                    .baseUnit("bytes")
                    .register(r);

            // Initialize S3 health check timers once
            s3HealthUpTimer = Timer.builder("dep.s3.check.latency")
                    .tag("result", "up")
                    .register(r);

            s3HealthDownTimer = Timer.builder("dep.s3.check.latency")
                    .tag("result", "down")
                    .register(r);

            s3HealthErrorCounter = Counter.builder("dep.s3.check.errors")
                    .tag("error", "general")
                    .register(r);
        };
    }

    /**
     * S3 dependency health check - runs every 5 minutes by default.
     * Provides early warning when bucket/IMDS/permissions break.
     * Can be disabled via metrics.s3.health.enabled=false for dev/test environments.
     */
    @Scheduled(fixedDelayString = "${metrics.s3.health.period:300000}")
    @ConditionalOnProperty(value = "metrics.s3.health.enabled", havingValue = "true", matchIfMissing = true)
    public void s3HealthPing() {
        long start = System.nanoTime();
        try {
            // Simple head bucket call to check connectivity and permissions
            s3Client.headBucket(builder -> builder.bucket(awsProperties.getS3().getBucketName()));

            // Record successful health check using pre-registered timer
            s3HealthUpTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (RuntimeException e) {
            // Record failed health check using pre-registered timer
            s3HealthDownTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            s3HealthErrorCounter.increment();
        }
    }
}
