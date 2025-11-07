package org.ddamme.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.JobType;
import org.ddamme.database.model.User;
import org.ddamme.metrics.Metrics;
import org.ddamme.service.ai.AiJobService;
import org.ddamme.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Transactional
@Slf4j
public class FileService {
    private final StorageService storageService;
    private final MetadataService metadataService;
    private final MeterRegistry meterRegistry;
    private final AiJobService aiJobService;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    @Value("${ai.worker.ocr.auto-create:true}")
    private boolean ocrAutoCreate;

    public FileService(StorageService storageService,
                       MetadataService metadataService,
                       MeterRegistry meterRegistry,
                       AiJobService aiJobService) {
        this.storageService = storageService;
        this.metadataService = metadataService;
        this.meterRegistry = meterRegistry;
        this.aiJobService = aiJobService;
    }

    public FileMetadata upload(User user, MultipartFile file) {
        long start = System.nanoTime();
        String result = "success";
        String contentFamily = "other";
        try {
            // Input validation with specific reject metrics
            if (file == null || file.isEmpty()) {
                Metrics.increment(meterRegistry, "fs.upload.rejects", "reason", "empty");
                throw new IllegalArgumentException("File cannot be empty");
            }
            if (file.getSize() > maxFileSize.toBytes()) {
                Metrics.increment(meterRegistry, "fs.upload.rejects", "reason", "too_large");
                throw new IllegalArgumentException("File too large. Max " + maxFileSize + ".");
            }

            String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String contentType = FileUtils.getContentTypeOrDefault(file.getContentType());
            contentFamily = Metrics.familyFromContentType(contentType);

            // Add environment prefix to separate dev/prod files in the same bucket
            String key = activeProfile + "/" + user.getId() + "/" + UUID.randomUUID() + "-" + FileUtils.sanitizeFilename(originalName);

            String storedKey = storageService.upload(file, key);

            FileMetadata meta =
                    FileMetadata.builder()
                            .user(user)
                            .originalFilename(originalName)
                            .storageKey(storedKey)
                            .size(file.getSize())
                            .contentType(contentType)
                            .build();

            // metrics - track upload success with content family
            Metrics.uploadBytes(meterRegistry).record(file.getSize());
            Metrics.increment(meterRegistry, "fs.upload.count", "result", "success", "content_family", contentFamily);

            FileMetadata savedMetadata = metadataService.save(meta);

            // Automatically create OCR job for PDFs and images (if enabled and AI service is available)
            log.debug("Job creation check: ocrAutoCreate={}, aiJobService={}, contentType={}, shouldOcr={}",
                    ocrAutoCreate, aiJobService != null, contentType, shouldOcr(contentType));

            if (ocrAutoCreate && aiJobService != null && shouldOcr(contentType)) {
                // Schedule job creation after commit so file_metadata row is visible to REQUIRES_NEW transaction
                final Long userId = user.getId();
                final Long fileId = savedMetadata.getId();
                final String filename = originalName;

                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                aiJobService.createJob(userId, fileId, JobType.OCR, 5, null);
                                log.info("Created OCR job for file {}: {}", fileId, filename);
                            } catch (Exception e) {
                                // Log error but don't propagate - upload should succeed even if job creation fails
                                log.error("Post-commit OCR job creation failed for file {}: {}",
                                        fileId, e.getMessage(), e);
                            }
                        }
                    });
            } else {
                log.warn("Skipping OCR job creation for file {}: ocrAutoCreate={}, aiJobService={}, shouldOcr={}",
                        originalName, ocrAutoCreate, aiJobService != null, shouldOcr(contentType));
            }

            return savedMetadata;
        } catch (RuntimeException e) {
            result = "failure";
            Metrics.increment(meterRegistry, "fs.upload.count", "result", "failure", "content_family", contentFamily);
            throw e;
        } finally {
            // End-to-end upload latency (controller→S3→DB)
            Metrics.recordTiming(
                    Metrics.timer(meterRegistry, "fs.upload.latency", "result", result, "content_family", contentFamily),
                    start);
        }
    }

    public String presignDownloadUrl(User user, Long fileId) {
        try {
            FileMetadata meta = metadataService.findOwnedById(user, fileId);
            String presignedUrl = storageService.generatePresignedDownloadUrl(
                    meta.getStorageKey(), meta.getOriginalFilename());
            Metrics.increment(meterRegistry, "fs.download.presign.count", "result", "success");
            return presignedUrl;
        } catch (RuntimeException e) {
            Metrics.increment(meterRegistry, "fs.download.presign.count", "result", "failure");
            throw e;
        }
    }

    public String presignViewUrl(User user, Long fileId) {
        try {
            FileMetadata meta = metadataService.findOwnedById(user, fileId);
            String presignedUrl = storageService.generatePresignedViewUrl(
                    meta.getStorageKey(), meta.getOriginalFilename());
            Metrics.increment(meterRegistry, "fs.view.presign.count", "result", "success");
            return presignedUrl;
        } catch (RuntimeException e) {
            Metrics.increment(meterRegistry, "fs.view.presign.count", "result", "failure");
            throw e;
        }
    }

    public void delete(User user, Long fileId) {
        try {
            FileMetadata meta = metadataService.findOwnedById(user, fileId);
            storageService.delete(meta.getStorageKey());
            metadataService.deleteById(fileId);
            Metrics.increment(meterRegistry, "fs.delete.count", "result", "success");
        } catch (RuntimeException e) {
            Metrics.increment(meterRegistry, "fs.delete.count", "result", "failure");
            throw e;
        }
    }

    private boolean shouldOcr(String contentType) {
        // Determine if the file type requires OCR processing
        return contentType.equals("application/pdf") ||
                contentType.startsWith("image/");
    }
}
