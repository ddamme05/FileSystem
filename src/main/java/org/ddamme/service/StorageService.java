package org.ddamme.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface StorageService {
    String upload(MultipartFile file);

    String upload(MultipartFile file, String storageKey);

    String generatePresignedDownloadUrl(String storageKey);

    String generatePresignedDownloadUrl(String key, String originalName);

    String generatePresignedViewUrl(String key, String originalName);

    void delete(String storageKey);

    /**
     * Download file from storage to local path (for OCR processing).
     */
    void downloadToFile(String storageKey, Path destination) throws IOException;
}

