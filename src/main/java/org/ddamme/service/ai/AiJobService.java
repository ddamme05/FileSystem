package org.ddamme.service.ai;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.database.model.AiJob;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.JobStatus;
import org.ddamme.database.model.JobType;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.AiJobRepository;
import org.ddamme.database.repository.MetadataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for creating and managing AI jobs.
 * 
 * Key features:
 * - Idempotent job creation (unique constraint on file_id + job_type)
 * - REQUIRES_NEW transaction isolation (decoupled from upload transaction)
 * - Race condition handling (duplicate key → read winner's ID)
 * 
 * See: docs/BEST_EFFORT_PATTERN.md for design rationale
 */
@Service
@Slf4j
public class AiJobService {

    private final AiJobRepository jobRepository;
    private final MetadataRepository metadataRepository;
    private final AiJobService self;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Self-injection for @Transactional proxying (must be @Lazy to break circular dependency)
    public AiJobService(AiJobRepository jobRepository, 
                        MetadataRepository metadataRepository,
                        @org.springframework.context.annotation.Lazy AiJobService self) {
        this.jobRepository = jobRepository;
        this.metadataRepository = metadataRepository;
        this.self = self;
    }

    /**
     * Create a new AI job for a file (public wrapper with race condition handling).
     * 
     * Wrapper method that handles unique constraint violations by reading the winner's job
     * in a separate transaction after the initial transaction rolls back.
     * 
     * @param userId User ID owning the file
     * @param fileId File ID to process
     * @param jobType Type of job (OCR, embedding, etc.)
     * @param priority Job priority (1-10, lower = higher priority)
     * @param dependsOnJobId Optional dependency job ID (null if no dependency)
     * @return Created job ID, or existing job ID if already exists
     */
    public Long createJob(long userId, long fileId, JobType jobType, int priority, Long dependsOnJobId) {
        try {
            // Call through self-reference to ensure @Transactional proxying works
            return self.createJobInternal(userId, fileId, jobType, priority, dependsOnJobId);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the job between our check and insert
            // Transaction has rolled back - now read the winner in a fresh transaction
            log.debug("Job creation race detected for fileId={}, jobType={}", fileId, jobType);
            
            // Winner-read loop (up to 3 attempts) in new transaction
            for (int attempt = 1; attempt <= 3; attempt++) {
                // Call through self-reference to ensure @Transactional proxying works
                Optional<AiJob> winner = self.readExistingJob(fileId, jobType);
                if (winner.isPresent()) {
                    log.debug("Read winner's job after race: fileId={}, jobType={}, jobId={}", 
                             fileId, jobType, winner.get().getId());
                    return winner.get().getId();
                }
                
                // Small backoff before retry
                try {
                    Thread.sleep(10 * attempt); // 10ms, 20ms, 30ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // If we still can't read the winner, log and return null (rare edge case)
            log.warn("Failed to read winner's job after race: fileId={}, jobType={}", fileId, jobType);
            return null;
        }
    }

    /**
     * Internal method to create a job within a REQUIRES_NEW transaction.
     * Throws DataIntegrityViolationException on unique constraint violation.
     * Public for Spring AOP proxying (called via self-reference).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createJobInternal(long userId, long fileId, JobType jobType, int priority, Long dependsOnJobId) {
        // Fast path: check if job already exists
        Optional<AiJob> existing = jobRepository.findByFileMetadata_IdAndJobType(fileId, jobType);
        if (existing.isPresent()) {
            log.debug("Job already exists: fileId={}, jobType={}, jobId={}", 
                     fileId, jobType, existing.get().getId());
            return existing.get().getId();
        }

        // Create lightweight entity references
        // Use EntityManager.getReference() to avoid loading User from DB
        // and prevent TransientPropertyValueException
        User userRef = entityManager.getReference(User.class, userId);

        FileMetadata file = metadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        // Create job
        AiJob job = AiJob.builder()
                .user(userRef)
                .fileMetadata(file)
                .jobType(jobType)
                .jobStatus(JobStatus.PENDING)
                .priority(priority)
                .dependsOnJobId(dependsOnJobId)
                .build();

        AiJob saved = jobRepository.save(job);
        log.info("Created job: id={}, fileId={}, jobType={}, priority={}", 
                 saved.getId(), fileId, jobType, priority);
        return saved.getId();
    }

    /**
     * Read existing job in a fresh transaction (called after race condition detected).
     * Public for Spring AOP proxying (called via self-reference).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<AiJob> readExistingJob(long fileId, JobType jobType) {
        return jobRepository.findByFileMetadata_IdAndJobType(fileId, jobType);
    }

    /**
     * Get job by ID.
     */
    @Transactional(readOnly = true)
    public Optional<AiJob> getJob(Long jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * Get all jobs for a user.
     */
    @Transactional(readOnly = true)
    public java.util.List<AiJob> getUserJobs(Long userId) {
        return jobRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get failed jobs for a user (for DLQ inspection).
     */
    @Transactional(readOnly = true)
    public java.util.List<AiJob> getUserFailedJobs(Long userId) {
        return jobRepository.findByUser_IdAndJobStatusOrderByCreatedAtDesc(userId, JobStatus.DLQ);
    }
}

