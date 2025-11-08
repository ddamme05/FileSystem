package org.ddamme.repository;

import org.ddamme.database.model.AiJob;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.JobStatus;
import org.ddamme.database.model.JobType;
import org.ddamme.database.model.Role;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.AiJobRepository;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.database.repository.UserRepository;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AI job claim functionality.
 * Validates the atomic job claiming mechanism with SKIP LOCKED.
 *
 * Key behaviors tested:
 * - Immediate claiming of jobs with NULL next_attempt_at
 * - Deferred job claiming based on next_attempt_at timestamp
 * - Dependency gating (jobs wait for parent completion)
 * - Attempts counter increments on each claim
 * - Concurrent worker safety (SKIP LOCKED prevents double-claiming)
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AiJobClaimIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MetadataRepository metadataRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private FileMetadata testFile;

    @BeforeEach
    void setUp() {
        // Clean slate - delete in correct order
        jobRepository.deleteAll();
        metadataRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user and file
        testUser = new User();
        testUser.setUsername("test-worker");
        testUser.setEmail("worker@test.com");
        testUser.setPassword("hashed");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);

        testFile = new FileMetadata();
        testFile.setUser(testUser);
        testFile.setOriginalFilename("test.pdf");
        testFile.setStorageKey("test-key");
        testFile.setSize(1000L);
        testFile.setContentType("application/pdf");
        testFile = metadataRepository.save(testFile);
    }

    @Test
    @DisplayName("Should claim job immediately when next_attempt_at is NULL")
    void shouldClaimJobWithNullNextAttemptAt() {
        // Given: Job with NULL next_attempt_at (ready immediately)
        AiJob job = createJob(JobType.OCR, null);
        job = jobRepository.save(job);

        // When: Claiming jobs
        List<Long> claimedIds = jobRepository.claimJobIds(10, "worker-1");

        // Then: Job is claimed
        assertThat(claimedIds).containsExactly(job.getId());

        // Verify job state updated
        AiJob claimed = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(claimed.getJobStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.getLockedBy()).isEqualTo("worker-1");
        assertThat(claimed.getLockedAt()).isNotNull();
        assertThat(claimed.getAttempts()).isEqualTo(1); // CRITICAL: attempts incremented
    }

    @Test
    @DisplayName("Should NOT claim job when next_attempt_at is in the future")
    void shouldNotClaimDeferredJob() {
        // Given: Job deferred to 5 minutes from now
        Instant futureTime = Instant.now().plus(5, ChronoUnit.MINUTES);
        AiJob job = createJob(JobType.OCR, futureTime);
        job = jobRepository.save(job);

        // When: Attempting to claim jobs now
        List<Long> claimedIds = jobRepository.claimJobIds(10, "worker-1");

        // Then: No jobs claimed
        assertThat(claimedIds).isEmpty();

        // Verify job state unchanged
        AiJob unchanged = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(unchanged.getJobStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(unchanged.getLockedBy()).isNull();
        assertThat(unchanged.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Should claim deferred job after time passes")
    void shouldClaimDeferredJobAfterTime() {
        // Given: Job deferred to 1 second ago (now claimable)
        Instant pastTime = Instant.now().minus(1, ChronoUnit.SECONDS);
        AiJob job = createJob(JobType.OCR, pastTime);
        job = jobRepository.save(job);

        // When: Claiming jobs
        List<Long> claimedIds = jobRepository.claimJobIds(10, "worker-1");

        // Then: Job is claimed (time has passed)
        assertThat(claimedIds).containsExactly(job.getId());

        AiJob claimed = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(claimed.getJobStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should NOT claim job with incomplete dependency")
    void shouldNotClaimJobWithPendingDependency() {
        // Given: Parent job still PENDING
        AiJob parentJob = createJob(JobType.OCR, null);
        parentJob = jobRepository.save(parentJob);

        // And: Child job depends on parent
        AiJob childJob = createJob(JobType.EMBED, null);
        childJob.setDependsOnJobId(parentJob.getId());
        childJob = jobRepository.save(childJob);

        // When: Claiming jobs
        List<Long> claimedIds = jobRepository.claimJobIds(10, "worker-1");

        // Then: Only parent claimed, not child
        assertThat(claimedIds).containsExactly(parentJob.getId());

        // Verify child still PENDING
        AiJob child = jobRepository.findById(childJob.getId()).orElseThrow();
        assertThat(child.getJobStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(child.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Should claim job after dependency completes")
    void shouldClaimJobAfterDependencyCompletes() {
        // Given: Parent job DONE
        AiJob parentJob = createJob(JobType.OCR, null);
        parentJob.setJobStatus(JobStatus.DONE);
        parentJob = jobRepository.save(parentJob);

        // And: Child job depends on completed parent
        AiJob childJob = createJob(JobType.EMBED, null);
        childJob.setDependsOnJobId(parentJob.getId());
        childJob = jobRepository.save(childJob);

        // When: Claiming jobs
        List<Long> claimedIds = jobRepository.claimJobIds(10, "worker-1");

        // Then: Child is now claimable
        assertThat(claimedIds).containsExactly(childJob.getId());

        AiJob claimed = jobRepository.findById(childJob.getId()).orElseThrow();
        assertThat(claimed.getJobStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should increment attempts counter on each claim")
    void shouldIncrementAttemptsOnEachClaim() {
        // Given: Job that will be claimed multiple times
        AiJob job = createJob(JobType.OCR, null);
        job = jobRepository.save(job);

        // When: First claim
        List<Long> claimed1 = jobRepository.claimJobIds(10, "worker-1");
        assertThat(claimed1).containsExactly(job.getId());
        AiJob after1 = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(after1.getAttempts()).isEqualTo(1);

        // And: Job fails, reset to PENDING for retry
        after1.setJobStatus(JobStatus.PENDING);
        after1.setLockedBy(null);
        after1.setLockedAt(null);
        after1.setNextAttemptAt(Instant.now().minus(1, ChronoUnit.SECONDS)); // ready now
        jobRepository.save(after1);

        // When: Second claim
        List<Long> claimed2 = jobRepository.claimJobIds(10, "worker-2");
        assertThat(claimed2).containsExactly(job.getId());
        AiJob after2 = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(after2.getAttempts()).isEqualTo(2); // CRITICAL: incremented again

        // And: Job fails again, reset to PENDING
        after2.setJobStatus(JobStatus.PENDING);
        after2.setLockedBy(null);
        after2.setLockedAt(null);
        after2.setNextAttemptAt(Instant.now().minus(1, ChronoUnit.SECONDS));
        jobRepository.save(after2);

        // When: Third claim
        List<Long> claimed3 = jobRepository.claimJobIds(10, "worker-3");
        assertThat(claimed3).containsExactly(job.getId());
        AiJob after3 = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(after3.getAttempts()).isEqualTo(3); // Keeps incrementing
    }

    @Test
    @DisplayName("Should prevent double-claiming with concurrent workers (SKIP LOCKED)")
    void shouldPreventDoubleClaiming() throws InterruptedException {
        // Given: 5 jobs ready to claim (each with unique file to avoid constraint violation)
        for (int i = 0; i < 5; i++) {
            // Create unique file for each job
            FileMetadata file = new FileMetadata();
            file.setUser(testUser);
            file.setOriginalFilename("test-" + i + ".pdf");
            file.setStorageKey("test-key-" + i);
            file.setSize(1000L);
            file.setContentType("application/pdf");
            file = metadataRepository.save(file);

            // Create job for this unique file
            AiJob job = new AiJob();
            job.setUser(testUser);
            job.setFileMetadata(file);
            job.setJobType(JobType.OCR);
            job.setJobStatus(JobStatus.PENDING);
            job.setPriority(5);
            job.setAttempts(0);
            job.setMaxAttempts(3);
            job.setNextAttemptAt(null); // Ready immediately
            jobRepository.save(job);
        }

        // And: 2 concurrent workers with separate transaction templates
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger worker1Claims = new AtomicInteger(0);
        AtomicInteger worker2Claims = new AtomicInteger(0);

        // Create transaction templates with REQUIRES_NEW to ensure separate transactions
        TransactionTemplate txTemplate1 = new TransactionTemplate(transactionManager);
        txTemplate1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TransactionTemplate txTemplate2 = new TransactionTemplate(transactionManager);
        txTemplate2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // When: Both workers claim simultaneously in separate transactions
        executor.submit(() -> {
            try {
                startLatch.await(); // Wait for go signal
                Integer claimed = txTemplate1.execute(status -> {
                    List<Long> jobIds = jobRepository.claimJobIds(10, "worker-1");
                    return jobIds.size();
                });
                worker1Claims.set(claimed != null ? claimed : 0);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await(); // Wait for go signal
                Integer claimed = txTemplate2.execute(status -> {
                    List<Long> jobIds = jobRepository.claimJobIds(10, "worker-2");
                    return jobIds.size();
                });
                worker2Claims.set(claimed != null ? claimed : 0);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // Start both workers
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: All jobs claimed exactly once (no double-claiming)
        assertThat(finished).isTrue();
        int totalClaimed = worker1Claims.get() + worker2Claims.get();
        assertThat(totalClaimed).isEqualTo(5);

        // Verify all jobs are RUNNING with correct lock
        List<AiJob> allJobs = jobRepository.findAll();
        assertThat(allJobs).hasSize(5);
        assertThat(allJobs).allMatch(j -> j.getJobStatus() == JobStatus.RUNNING);
        assertThat(allJobs).allMatch(j -> j.getLockedBy() != null);
        assertThat(allJobs).allMatch(j -> j.getLockedAt() != null);
        assertThat(allJobs).allMatch(j -> j.getAttempts() == 1);

        // Verify workers claimed different jobs (no overlap)
        long worker1Jobs = allJobs.stream().filter(j -> "worker-1".equals(j.getLockedBy())).count();
        long worker2Jobs = allJobs.stream().filter(j -> "worker-2".equals(j.getLockedBy())).count();
        assertThat(worker1Jobs + worker2Jobs).isEqualTo(5);
    }

    @Test
    @DisplayName("Should respect priority and created_at ordering")
    void shouldRespectClaimOrdering() {
        // Given: Jobs with different priorities and timestamps
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);

        // Low priority (10), oldest
        AiJob job1 = createJob(JobType.OCR, null);
        job1.setPriority(10);
        job1.setCreatedAt(base);
        job1 = jobRepository.save(job1);

        // High priority (1), newest
        AiJob job2 = createJob(JobType.EMBED, null);
        job2.setPriority(1);
        job2.setCreatedAt(base.plus(5, ChronoUnit.MINUTES));
        job2 = jobRepository.save(job2);

        // Medium priority (5), middle
        AiJob job3 = createJob(JobType.PII_SCAN, null);
        job3.setPriority(5);
        job3.setCreatedAt(base.plus(2, ChronoUnit.MINUTES));
        job3 = jobRepository.save(job3);

        // When: Claiming in order
        List<Long> claimed = jobRepository.claimJobIds(3, "worker-1");

        // Then: Claimed in priority order (1, 5, 10)
        assertThat(claimed).containsExactly(job2.getId(), job3.getId(), job1.getId());
    }

    @Test
    @DisplayName("Should respect next_attempt_at over priority")
    void shouldRespectNextAttemptAtOverPriority() {
        // Given: High priority job deferred, low priority job ready
        AiJob highPriorityDeferred = createJob(JobType.OCR, Instant.now().plus(1, ChronoUnit.HOURS));
        highPriorityDeferred.setPriority(1); // High priority
        highPriorityDeferred = jobRepository.save(highPriorityDeferred);

        AiJob lowPriorityReady = createJob(JobType.EMBED, null);
        lowPriorityReady.setPriority(10); // Low priority but ready now
        lowPriorityReady = jobRepository.save(lowPriorityReady);

        // When: Claiming jobs
        List<Long> claimed = jobRepository.claimJobIds(10, "worker-1");

        // Then: Low priority job claimed first (it's ready)
        assertThat(claimed).containsExactly(lowPriorityReady.getId());
    }

    // Helper method to create test jobs
    private AiJob createJob(JobType type, Instant nextAttemptAt) {
        AiJob job = new AiJob();
        job.setUser(testUser);
        job.setFileMetadata(testFile);
        job.setJobType(type);
        job.setJobStatus(JobStatus.PENDING);
        job.setPriority(5); // Default priority
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setNextAttemptAt(nextAttemptAt);
        return job;
    }
}

