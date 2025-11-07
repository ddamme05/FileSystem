package org.ddamme.database.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * AI Job entity for async processing of files.
 * Supports: OCR, embeddings, PII detection, redaction, summarization
 *
 * Features:
 * - Retry logic with exponential backoff
 * - Dead Letter Queue (DLQ) for permanent failures
 * - Worker coordination with row-level locking
 * - Job dependencies for sequential processing
 */
@Entity
@Table(name = "ai_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata fileMetadata;

    // Helper methods to get IDs without loading lazy relationships
    @Transient
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    @Transient
    public Long getFileId() {
        return fileMetadata != null ? fileMetadata.getId() : null;
    }

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "job_status", nullable = false)
    @Builder.Default
    private JobStatus jobStatus = JobStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 5;  // Lower number = higher priority (1-10)

    // Retry logic
    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    // Worker coordination
    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    // Job dependencies
    @Column(name = "depends_on_job_id")
    private Long dependsOnJobId;

    // Input/output data (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_params", columnDefinition = "JSONB")
    private Map<String, Object> inputParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "JSONB")
    private Map<String, Object> outputData;

    // Error tracking
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Database-managed timestamp (updated by trigger on every UPDATE).
     * DO NOT set manually - let the trigger handle it.
     * 
     * See: V3_4__add_updated_at_trigger.sql
     */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Check if job is ready to be attempted again.
     */
    public boolean isReadyForRetry() {
        return nextAttemptAt == null || Instant.now().isAfter(nextAttemptAt);
    }

    /**
     * Check if job has remaining retry attempts.
     */
    public boolean hasRemainingAttempts() {
        return attempts < maxAttempts;
    }

    /**
     * Calculate next retry delay with exponential backoff.
     * Formula: 2^attempt minutes, max 60 minutes, ±25% jitter
     */
    public Duration calculateNextRetryDelay() {
        // Base delay: 2^attempt minutes (1min, 2min, 4min, 8min, ...)
        long baseDelayMinutes = (long) Math.pow(2, attempts);
        // Cap at 60 minutes
        long cappedMinutes = Math.min(baseDelayMinutes, 60);
        // Add jitter: ±25%
        double jitterFactor = 0.75 + (Math.random() * 0.5); // 0.75 to 1.25
        long jitteredMinutes = (long) (cappedMinutes * jitterFactor);

        return Duration.ofMinutes(jitteredMinutes);
    }
}
