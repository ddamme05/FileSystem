package org.ddamme.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.config.AiWorkerProperties;
import org.ddamme.database.model.AiJob;
import org.ddamme.database.model.JobStatus;
import org.ddamme.database.repository.AiJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Heart of the AI job system.
 *
 * Responsibilities:
 * - Poll for pending jobs
 * - Claim jobs atomically with SKIP LOCKED
 * - Execute jobs via JobHandler implementations
 * - Retry failed jobs with exponential backoff
 * - Reclaim stuck jobs (worker died/hung)
 * - Move permanently failed jobs to DLQ
 */
@Service
@ConditionalOnProperty(name = "ai.worker.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AiJobScheduler {

    private final AiJobRepository jobRepository;
    private final AiWorkerProperties properties;
    private final List<JobHandler> jobHandlers;
    private final MeterRegistry meterRegistry;
    private final Executor aiJobExecutor;

    private String workerId;

    /**
     * Explicit constructor with @Qualifier for aiJobExecutor bean.
     * Lombok's @RequiredArgsConstructor doesn't copy field-level @Qualifier to constructor params.
     */
    public AiJobScheduler(
            AiJobRepository jobRepository,
            AiWorkerProperties properties,
            List<JobHandler> jobHandlers,
            MeterRegistry meterRegistry,
            @Qualifier("aiJobExecutor") Executor aiJobExecutor) {
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.jobHandlers = jobHandlers;
        this.meterRegistry = meterRegistry;
        this.aiJobExecutor = aiJobExecutor;
    }

    /**
     * Initialize worker ID and register gauges on startup.
     * 
     * Gauges use supplier pattern: registered once, queried on-demand by monitoring system.
     * See: docs/SUPPLIER_PATTERN.md
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            workerId = hostname + "-" + System.currentTimeMillis();
            log.info("AI worker initialized with ID: {}", workerId);
        } catch (Exception e) {
            workerId = "worker-" + System.currentTimeMillis();
            log.warn("Failed to get hostname, using fallback worker ID: {}", workerId);
        }
        
        registerGauges();
    }
    
    /**
     * Register gauges with supplier pattern (Micrometer best practice).
     * 
     * Supplier functions query DB on-demand when monitoring system scrapes.
     * No scheduled updates needed - values are always fresh.
     * 
     * Registered once at startup; monitoring system polls on its schedule (e.g., Prometheus every 15s).
     */
    private void registerGauges() {
        // Pending jobs gauge (queries DB on each scrape)
        Gauge.builder("ai.jobs.pending", jobRepository, 
                repo -> repo.countByJobStatus(JobStatus.PENDING))
            .description("Current pending job queue depth")
            .tag("status", "pending")
            .baseUnit("jobs")
            .register(meterRegistry);
            
        // Running jobs gauge (queries DB on each scrape)
        Gauge.builder("ai.jobs.running", jobRepository,
                repo -> repo.countByJobStatus(JobStatus.RUNNING))
            .description("Currently executing jobs")
            .tag("status", "running")
            .baseUnit("jobs")
            .register(meterRegistry);
            
        log.info("AI job queue gauges registered (supplier pattern)");
    }

    /**
     * Poll for jobs and execute them.
     * Runs every ai.worker.poll-interval (default 5 seconds).
     */
    @Scheduled(fixedDelayString = "${ai.worker.poll-interval:5000}")
    public void pollJobs() {  // ⬅ removed @Transactional
        try {
            List<Long> jobIds = claimReadyJobs(); // short tx, commits immediately

            if (!jobIds.isEmpty()) {
                log.debug("Claimed {} jobs: {}", jobIds.size(), jobIds);
                recordMetric("ai.jobs.claimed", jobIds.size());

                // Dispatch each job to executor pool
                jobIds.forEach(id -> aiJobExecutor.execute(() -> executeJobById(id)));
            }
        } catch (Exception e) {
            log.error("Error polling jobs", e);
        }
    }

    /**
     * Claim ready jobs in a short transaction that commits immediately.
     */
    @Transactional
    public List<Long> claimReadyJobs() {
        return jobRepository.claimJobIds(properties.getBatchSize(), workerId);
    }

    /**
     * Reclaim stuck jobs (worker died or hung).
     * Runs every ai.worker.reclaim-interval (default 60 seconds).
     */
    @Scheduled(fixedDelayString = "${ai.worker.reclaim-interval:60000}")
    @Transactional
    public void reclaimStuckJobs() {
        try {
            Instant staleThreshold = Instant.now().minus(properties.getStaleJobTimeout());
            List<AiJob> stuckJobs = jobRepository.findByJobStatusAndLockedAtBefore(
                    JobStatus.RUNNING, staleThreshold);

            if (!stuckJobs.isEmpty()) {
                log.warn("Reclaiming {} stuck jobs (locked before {})",
                        stuckJobs.size(), staleThreshold);

                for (AiJob job : stuckJobs) {
                    log.warn("Reclaiming stuck job: id={}, type={}, lockedBy={}, lockedAt={}",
                            job.getId(), job.getJobType(), job.getLockedBy(), job.getLockedAt());

                    // Reset job for retry with backoff
                    // NOTE: Do NOT increment attempts here - claimJobIds() will increment on next claim
                    // Incrementing twice would prematurely DLQ jobs
                    job.setJobStatus(JobStatus.PENDING);
                    job.setLockedBy(null);
                    job.setLockedAt(null);
                    
                    // Calculate retry delay based on current attempts (will be incremented on claim)
                    Duration retryDelay = job.calculateNextRetryDelay();
                    job.setNextAttemptAt(Instant.now().plus(retryDelay));

                    jobRepository.save(job);
                    recordMetric("ai.jobs.reclaimed", 1);
                }
            }
        } catch (Exception e) {
            log.error("Error reclaiming stuck jobs", e);
        }
    }

    /**
     * Execute a job by ID in a new transaction.
     * New transaction ensures job failure doesn't rollback other jobs in batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeJobById(Long jobId) {
        try {
            // Fetch fresh job from DB
            Optional<AiJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found: {}", jobId);
                return;
            }

            AiJob job = jobOpt.get();
            
            // Inject maxAttempts from config (allows per-environment tuning)
            job.setMaxAttempts(properties.getMaxAttempts());
            
            log.info("Executing job: id={}, type={}, fileId={}, attempt={}/{}",
                    job.getId(), job.getJobType(), job.getFileId(), 
                    job.getAttempts(), job.getMaxAttempts());

            Instant startTime = Instant.now();

            try {
                // Find handler for this job type
                JobHandler handler = findHandler(job);

                // Execute job
                handler.execute(job);

                // Mark as completed (clear scheduling fields)
                job.setJobStatus(JobStatus.DONE);
                job.setCompletedAt(Instant.now());
                job.setLockedBy(null);
                job.setLockedAt(null);
                job.setNextAttemptAt(null);  // Clear scheduling field
                job.setErrorMessage(null);

                jobRepository.save(job);

                Duration duration = Duration.between(startTime, Instant.now());
                log.info("Job completed: id={}, type={}, duration={}ms",
                        job.getId(), job.getJobType(), duration.toMillis());

                recordMetric("ai.jobs.completed", 1, "result", "success", "type", job.getJobType().name());

            } catch (Exception e) {
                log.error("Job failed: id={}, type={}, error={}",
                        job.getId(), job.getJobType(), e.getMessage(), e);

                handleJobFailure(job, e);

                recordMetric("ai.jobs.completed", 1, "result", "failure", "type", job.getJobType().name());
            }

        } catch (Exception e) {
            log.error("Error executing job {}", jobId, e);
        }
    }

    /**
     * Handle job failure with retry logic.
     * 
     * Note: attempts is already incremented in the claim CTE (claimJobIds query).
     * Do NOT increment here to avoid double-incrementing.
     */
    private void handleJobFailure(AiJob job, Exception error) {
        job.setErrorMessage(error.getMessage());
        // Note: attempts already incremented on claim, not here

        if (job.hasRemainingAttempts()) {
            // Retry with exponential backoff
            Duration retryDelay = job.calculateNextRetryDelay();
            job.setNextAttemptAt(Instant.now().plus(retryDelay));
            job.setJobStatus(JobStatus.PENDING);
            job.setLockedBy(null);
            job.setLockedAt(null);

            jobRepository.save(job);

            log.warn("Job will retry: id={}, attempt={}/{}, nextAttempt={}",
                    job.getId(), job.getAttempts(), job.getMaxAttempts(), job.getNextAttemptAt());
        } else {
            // Move to Dead Letter Queue with structured error details for faster triage
            job.setJobStatus(JobStatus.DLQ);
            job.setLockedBy(null);
            job.setLockedAt(null);
            
            // Store structured data in outputData (JSONB Map) for DLQ triage
            // Avoids escaping bugs and preserves types
            String errorCode = extractErrorCode(error);
            String errorType = error.getClass().getSimpleName();
            
            job.setOutputData(java.util.Map.of(
                "error_code", errorCode,
                "error_type", errorType,
                "file_id", job.getFileId(),
                "job_type", job.getJobType().name(),
                "attempts", job.getAttempts(),
                "message", error.getMessage() != null ? error.getMessage() : "Unknown error",
                "timestamp", Instant.now().toString()
            ));
            
            // Keep simple error message for quick scanning
            job.setErrorMessage(errorCode + ": " + error.getMessage());

            jobRepository.save(job);

            log.error("Job moved to DLQ: id={}, type={}, attempts={}, errorCode={}",
                    job.getId(), job.getJobType(), job.getAttempts(), errorCode);

            // Avoid error_code tag to prevent cardinality explosion
            recordMetric("ai.jobs.dlq", 1, "type", job.getJobType().name());
        }
    }
    
    /**
     * Extract error code from exception message for structured DLQ logging.
     * Looks for patterns like "PDF_ENCRYPTED:", "S3_NOT_FOUND:", etc.
     */
    private String extractErrorCode(Exception error) {
        if (error.getMessage() != null && error.getMessage().contains(":")) {
            String[] parts = error.getMessage().split(":", 2);
            String potentialCode = parts[0].trim();
            // Validate it looks like an error code (UPPER_SNAKE_CASE)
            if (potentialCode.matches("[A-Z_]+")) {
                return potentialCode;
            }
        }
        // Fallback to exception class name
        return error.getClass().getSimpleName().toUpperCase();
    }

    /**
     * Find appropriate handler for job.
     */
    private JobHandler findHandler(AiJob job) {
        return jobHandlers.stream()
                .filter(handler -> handler.supports(job))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No handler found for job type: " + job.getJobType()));
    }

    /**
     * Record metric with tags.
     */
    private void recordMetric(String name, double value, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment(value);
    }
}

