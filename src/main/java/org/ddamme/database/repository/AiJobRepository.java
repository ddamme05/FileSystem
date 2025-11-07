package org.ddamme.database.repository;

import org.ddamme.database.model.AiJob;
import org.ddamme.database.model.JobStatus;
import org.ddamme.database.model.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AI job queue operations.
 * Key feature: Atomic job claiming with SKIP LOCKED for concurrent workers.
 *
 * Note: Native SQL is used only where necessary (FOR UPDATE SKIP LOCKED).
 * All other queries use Spring Data JPA derived methods or JPQL to leverage
 * automatic enum type handling via @JdbcType.
 */
@Repository
public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    /**
     * Atomically claim jobs for processing using SKIP LOCKED.
     *
     * This method:
     * 1. Finds ready jobs (PENDING, time to run, dependencies satisfied)
     * 2. Orders by next_attempt_at ASC NULLS FIRST, priority ASC, created_at ASC
     * 3. Locks rows with FOR UPDATE SKIP LOCKED (no blocking)
     * 4. Updates status to RUNNING, sets locked_by, and increments attempts
     * 5. Returns claimed job IDs
     *
     * Why native SQL: JPA doesn't support FOR UPDATE SKIP LOCKED
     * Why return IDs: Avoids JPA mapping issues with RETURNING clause
     * Why CAST: Native SQL bypasses @JdbcType, needs explicit enum casting
     */
    @Modifying
    @Query(value = """
        WITH candidates AS (
            SELECT id
            FROM ai_jobs
            WHERE job_status = CAST('PENDING' AS job_status)
              AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
              AND (depends_on_job_id IS NULL
                   OR EXISTS (
                       SELECT 1
                       FROM ai_jobs d
                       WHERE d.id = ai_jobs.depends_on_job_id
                         AND d.job_status = CAST('DONE' AS job_status)
                   ))
            ORDER BY next_attempt_at ASC NULLS FIRST, priority ASC, created_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE ai_jobs j
        SET job_status = CAST('RUNNING' AS job_status),
            locked_by = :workerId,
            locked_at = NOW(),
            attempts = j.attempts + 1,
            updated_at = NOW()
        FROM candidates c
        WHERE j.id = c.id
        RETURNING j.id
        """, nativeQuery = true)
    List<Long> claimJobIds(@Param("batchSize") int batchSize,
                           @Param("workerId") String workerId);

    /**
     * Find jobs stuck in RUNNING state (worker died or hung).
     * Uses Spring Data JPA derived method - @JdbcType handles enum binding automatically.
     */
    List<AiJob> findByJobStatusAndLockedAtBefore(JobStatus status, Instant before);

    /**
     * Count jobs by status for monitoring metrics.
     * Uses Spring Data JPA derived method - no manual CAST needed.
     */
    long countByJobStatus(JobStatus status);

    /**
     * Find existing job for a file (prevents duplicates).
     * Uses underscore notation to navigate through fileMetadata relationship.
     * Note: Both findByFileMetadataId and findByFileMetadata_Id work; underscore is clearer.
     */
    Optional<AiJob> findByFileMetadata_IdAndJobType(Long fileId, JobType jobType);

    /**
     * Get all jobs for a user (admin/debugging).
     * Uses underscore notation to navigate through user relationship.
     */
    List<AiJob> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Get failed jobs for a user.
     * Uses underscore notation to navigate through user relationship.
     */
    List<AiJob> findByUser_IdAndJobStatusOrderByCreatedAtDesc(Long userId, JobStatus status);
}
