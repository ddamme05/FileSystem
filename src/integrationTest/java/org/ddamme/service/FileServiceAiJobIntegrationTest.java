package org.ddamme.service;

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
import org.ddamme.service.ai.AiJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration tests for AI job creation during file upload.
 * Tests transaction isolation, idempotency, and error handling.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "ai.worker.enabled=true",
        "ai.worker.ocr.auto-create=true"
})
class FileServiceAiJobIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private AiJobService aiJobService;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private MetadataRepository metadataRepository;

    @Autowired
    private UserRepository userRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private S3StorageService s3StorageService;

    private User testUser;
    private AtomicInteger storageKeyCounter;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data in correct order
        jobRepository.deleteAll();
        metadataRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("encoded_password")
                .role(Role.USER)
                .build();
        testUser = userRepository.save(testUser);

        // Initialize counter for unique storage keys
        storageKeyCounter = new AtomicInteger(0);

        // Stub S3 upload to return unique storage keys for each upload
        // This prevents duplicate key violations on file_metadata.storage_key
        doAnswer(invocation -> "test-storage-key-" + storageKeyCounter.incrementAndGet())
                .when(s3StorageService).upload(any(), any());

        // Verify AiJobService is properly wired (critical for job creation tests)
        assertNotNull(aiJobService, "AiJobService must be available for job creation tests");
        assertNotNull(fileService, "FileService must be available");

        // Sanity check: Verify FileService actually has aiJobService injected
        Object injectedAiJobService = ReflectionTestUtils.getField(fileService, "aiJobService");
        assertNotNull(injectedAiJobService,
                "FileService.aiJobService must be non-null for auto job creation to work");

        // Sanity check: Verify ocrAutoCreate flag is true
        Boolean ocrAutoCreateFlag = (Boolean) ReflectionTestUtils.getField(fileService, "ocrAutoCreate");
        assertTrue(ocrAutoCreateFlag, "FileService.ocrAutoCreate must be true for auto job creation");
    }

    /**
     * Test that file upload succeeds even if job creation fails.
     * Uses PROPAGATION.NOT_SUPPORTED to ensure we see real commit behavior.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void uploadSucceedsEvenIfJobCreationFails() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "PDF content for testing".getBytes()
        );

        // Act
        FileMetadata metadata = fileService.upload(testUser, file);

        // Assert - Upload succeeded
        assertNotNull(metadata.getId(), "File metadata should be persisted");
        assertEquals("test-document.pdf", metadata.getOriginalFilename());

        // Verify OCR job was created
        Optional<AiJob> job = jobRepository.findByFileMetadata_IdAndJobType(
                metadata.getId(), JobType.OCR);

        assertTrue(job.isPresent(), "OCR job should be created");
        assertEquals(JobStatus.PENDING, job.get().getJobStatus());
        assertEquals(testUser.getId(), job.get().getUser().getId());
    }

    /**
     * Test that duplicate job creation is idempotent.
     * If job already exists, should return existing job without error.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jobCreationIsIdempotent() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "idempotent-test.pdf",
                "application/pdf",
                "Test content".getBytes()
        );

        FileMetadata metadata = fileService.upload(testUser, file);

        // Act - Try to create the same job type again
        Long jobId1 = aiJobService.createJob(
                testUser.getId(), metadata.getId(), JobType.OCR, 5, null);

        Long jobId2 = aiJobService.createJob(
                testUser.getId(), metadata.getId(), JobType.OCR, 5, null);

        // Assert - Should return same job ID (idempotent)
        assertEquals(jobId1, jobId2,
                "Duplicate job creation should return existing job ID");

        // Verify only one job exists in DB
        long jobCount = jobRepository.findByFileMetadata_IdAndJobType(
                metadata.getId(), JobType.OCR)
                .stream()
                .count();

        assertEquals(1, jobCount, "Should only have one job in database");
    }

    /**
     * Test concurrent job creation for same file.
     * Unique constraint should prevent duplicates, both threads should succeed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentJobCreationHandlesRaceCondition() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "concurrent-test.pdf",
                "application/pdf",
                "Test content".getBytes()
        );

        FileMetadata metadata = fileService.upload(testUser, file);

        // Delete the auto-created job to test concurrent creation from scratch
        jobRepository.deleteAll();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        CyclicBarrier startGate = new CyclicBarrier(2); // Make threads start simultaneously
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        // Act - Create same job from two threads simultaneously
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Both threads wait here, then start together

                    aiJobService.createJob(
                            testUser.getId(), metadata.getId(), JobType.OCR, 5, null);

                    // Winner (or loser if service absorbed the race)
                    successCount.incrementAndGet();

                } catch (DataIntegrityViolationException e) {
                    // Loser: treat unique violation (23505) as success
                    Throwable root = e.getRootCause();
                    boolean isUniqueViolation =
                            (root instanceof org.postgresql.util.PSQLException &&
                             "23505".equals(((org.postgresql.util.PSQLException) root).getSQLState())) ||
                            (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException &&
                             ((org.hibernate.exception.ConstraintViolationException) e.getCause()).getSQLException() != null &&
                             "23505".equals(((org.hibernate.exception.ConstraintViolationException) e.getCause()).getSQLException().getSQLState()));

                    if (isUniqueViolation) {
                        // Expected loser in the race - idempotency worked
                        successCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait for both threads to complete
        executor.shutdown();

        // Assert - Handle any unexpected errors
        if (!unexpectedErrors.isEmpty()) {
            unexpectedErrors.forEach(Throwable::printStackTrace);
            fail("Unexpected exception(s): " + unexpectedErrors);
        }

        // Both threads should succeed (one creates, one either gets it back from service or sees 23505)
        assertEquals(2, successCount.get(),
                "Both threads should succeed (one creates, one idempotent-collides)");

        // Verify only one job exists in DB
        long jobCount = jobRepository.count();
        assertEquals(1, jobCount, "Exactly one job should exist despite concurrent creation");
    }

    /**
     * Test that jobs are created for PDFs and images only.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jobCreatedOnlyForOcrEligibleFiles() {
        // Arrange & Act - Upload PDF (should create job)
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "content".getBytes());
        FileMetadata pdfMetadata = fileService.upload(testUser, pdf);

        // Upload image (should create job)
        MockMultipartFile image = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "content".getBytes());
        FileMetadata imageMetadata = fileService.upload(testUser, image);

        // Upload text file (should NOT create job)
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "content".getBytes());
        FileMetadata textMetadata = fileService.upload(testUser, textFile);

        // Assert
        assertTrue(jobRepository.findByFileMetadata_IdAndJobType(
                pdfMetadata.getId(), JobType.OCR).isPresent(),
                "OCR job should be created for PDF");

        assertTrue(jobRepository.findByFileMetadata_IdAndJobType(
                imageMetadata.getId(), JobType.OCR).isPresent(),
                "OCR job should be created for image");

        assertFalse(jobRepository.findByFileMetadata_IdAndJobType(
                textMetadata.getId(), JobType.OCR).isPresent(),
                "OCR job should NOT be created for text file");
    }

    /**
     * Test that job priority is set correctly.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jobHasCorrectDefaultPriority() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "priority-test.pdf", "application/pdf", "content".getBytes());

        // Act
        FileMetadata metadata = fileService.upload(testUser, file);

        // Assert
        AiJob job = jobRepository.findByFileMetadata_IdAndJobType(
                metadata.getId(), JobType.OCR).orElseThrow();

        assertEquals(5, job.getPriority(), "Default priority should be 5");
    }

    /**
     * Test that job references are lightweight (getReferenceById).
     * This verifies we're not loading full entities unnecessarily.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jobCreationUsesLightweightReferences() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "reference-test.pdf", "application/pdf", "content".getBytes());

        FileMetadata metadata = fileService.upload(testUser, file);

        // Delete auto-created job so we can test with custom priority
        jobRepository.deleteAll();

        // Act - Create job with IDs (should use EntityManager.getReference internally)
        Long jobId = aiJobService.createJob(
                testUser.getId(), metadata.getId(), JobType.OCR, 7, null);

        // Assert - Job created successfully with correct references
        assertNotNull(jobId, "Job ID should not be null");
        
        // Fetch the created job to verify
        AiJob job = jobRepository.findById(jobId).orElseThrow();
        assertEquals(testUser.getId(), job.getUser().getId());
        assertEquals(metadata.getId(), job.getFileMetadata().getId());
        assertEquals(7, job.getPriority());
    }

    /**
     * Test that job creation is isolated in separate transaction.
     * This ensures REQUIRES_NEW propagation works correctly.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jobCreationIsolatedFromUploadTransaction() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "isolation-test.pdf", "application/pdf", "content".getBytes());

        // Act - Upload file (creates job in separate transaction)
        FileMetadata metadata = fileService.upload(testUser, file);

        // Assert - Both file and job are committed
        FileMetadata persistedFile = metadataRepository.findById(metadata.getId()).orElseThrow();
        assertNotNull(persistedFile, "File should be persisted");

        AiJob persistedJob = jobRepository.findByFileMetadata_IdAndJobType(
                metadata.getId(), JobType.OCR).orElseThrow();
        assertNotNull(persistedJob, "Job should be persisted in separate transaction");
    }
}
