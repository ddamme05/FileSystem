package org.ddamme.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(MultipartFile file);
    String generatePresignedDownloadUrl(String storageKey);
    void delete(String storageKey);
} 