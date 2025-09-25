package org.ddamme.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.ddamme.database.repository.MetadataRepository;
import org.springframework.beans.factory.annotation.Value;
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
 * 
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
    };
  }

  /**
   * S3 dependency health check - runs every 60 seconds by default.
   * Provides early warning when bucket/IMDS/permissions break.
   * Can be disabled via metrics.s3.health.enabled=false for dev/test environments.
   */
  @Scheduled(fixedDelayString = "${metrics.s3.health.period:60000}")
  @ConditionalOnProperty(value = "metrics.s3.health.enabled", havingValue = "true", matchIfMissing = true)
  public void s3HealthPing() {
    long start = System.nanoTime();
    try {
      // Simple head bucket call to check connectivity and permissions
      s3Client.headBucket(builder -> builder.bucket(awsProperties.getS3().getBucketName()));
      
      // Record successful health check
      Timer.builder("dep.s3.check.latency")
          .tag("result", "up")
          .register(registry)
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    } catch (RuntimeException e) {
      // Record failed health check with error type
      Timer.builder("dep.s3.check.latency")
          .tag("result", "down")
          .register(registry)
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      
      Counter.builder("dep.s3.check.errors")
          .tag("error", e.getClass().getSimpleName())
          .register(registry)
          .increment();
    }
  }
}
