package org.ddamme.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.metrics.Metrics;
import org.ddamme.util.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final StorageService storageService;
    private final MetadataService metadataService;
    private final MeterRegistry meterRegistry;
    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;

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
            String key =
                    user.getId() + "/" + UUID.randomUUID() + "-" + FileUtils.sanitizeFilename(originalName);

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
            return metadataService.save(meta);
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
}
