package org.ddamme.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Vendor-agnostic metrics utility to keep metric names/tags consistent and out of app logic.
 * Works with any Micrometer registry (OTLP, Prometheus, etc.).
 * Notes:
 * - Keep tag cardinality LOW in prod (avoid per-user/principal if possible).
 * - Micrometer will de-duplicate meters with same name+tags; multiple register() calls are fine.
 */
public final class Metrics {
    private Metrics() {
    }

    /**
     * Creates or retrieves a distribution summary for tracking upload file sizes.
     * Includes p95/p99 percentiles and histogram buckets for Datadog visualization.
     */
    public static DistributionSummary uploadBytes(MeterRegistry registry) {
        return DistributionSummary.builder("fs.upload.bytes")
                .baseUnit("bytes")
                .publishPercentiles(0.95, 0.99)  // explicit p95/p99 for Datadog
                .publishPercentileHistogram()   // also send histogram buckets
                .register(registry);
    }

    /**
     * Increments a counter with optional key-value tag pairs.
     *
     * @param registry         the meter registry
     * @param metricName       the name of the counter metric
     * @param tagKeyValuePairs optional tag pairs (key1, value1, key2, value2, ...)
     */
    public static void increment(MeterRegistry registry, String metricName, String... tagKeyValuePairs) {
        Counter.Builder builder = Counter.builder(metricName);
        if (tagKeyValuePairs != null) {
            for (int i = 0; i + 1 < tagKeyValuePairs.length; i += 2) {
                builder = builder.tag(tagKeyValuePairs[i], tagKeyValuePairs[i + 1]);
            }
        }
        builder.register(registry).increment();
    }

    /**
     * Creates or retrieves a timer with percentiles for tracking operation latency.
     *
     * @param registry         the meter registry
     * @param metricName       the name of the timer metric
     * @param tagKeyValuePairs optional tag pairs (key1, value1, key2, value2, ...)
     */
    public static Timer timer(MeterRegistry registry, String metricName, String... tagKeyValuePairs) {
        Timer.Builder builder = Timer.builder(metricName)
                .publishPercentileHistogram()
                .publishPercentiles(0.95, 0.99);
        if (tagKeyValuePairs != null) {
            for (int i = 0; i + 1 < tagKeyValuePairs.length; i += 2) {
                builder = builder.tag(tagKeyValuePairs[i], tagKeyValuePairs[i + 1]);
            }
        }
        return builder.register(registry);
    }

    /**
     * Records timing for an operation, handling both success and failure cases.
     *
     * @param timer      the timer to record to
     * @param startNanos the start time in nanoseconds (from System.nanoTime())
     */
    public static void recordTiming(Timer timer, long startNanos) {
        timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Determines content family from MIME type for better metric grouping.
     *
     * @param contentType the MIME type (e.g., "image/jpeg", "application/pdf")
     * @return content family: "image", "pdf", "doc", or "other"
     */
    public static String familyFromContentType(String contentType) {
        if (contentType == null) return "other";

        String lowerType = contentType.toLowerCase(Locale.ROOT);
        if (lowerType.startsWith("image/")) return "image";
        if (lowerType.equals("application/pdf")) return "pdf";
        if (lowerType.startsWith("text/")) return "doc";
        if (lowerType.contains("word") || lowerType.contains("excel") || lowerType.contains("ppt")) return "doc";
        return "other";
    }

}

