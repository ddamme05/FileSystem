package org.ddamme.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(MultipartFile file);
    String upload(MultipartFile file, String storageKey);
    String generatePresignedDownloadUrl(String storageKey);
    String generatePresignedDownloadUrl(String key, String originalName);
    void delete(String storageKey);
}