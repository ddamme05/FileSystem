package org.ddamme.service;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final StorageService storageService;
    private final MetadataService metadataService;
    
    public FileMetadata upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String key = user.getId() + "/" + UUID.randomUUID() + "-" + sanitizeFilename(originalName);
        
        String storedKey = storageService.upload(file, key);
        
        FileMetadata meta = FileMetadata.builder()
            .user(user)
            .originalFilename(originalName)
            .storageKey(storedKey)
            .size(file.getSize())
            .contentType(file.getContentType())
            .build();
        
        return metadataService.save(meta);
    }
    
    public String presignDownloadUrl(User user, Long fileId) {
        FileMetadata meta = metadataService.findOwnedById(user, fileId);
        return storageService.generatePresignedDownloadUrl(
            meta.getStorageKey(), 
            meta.getOriginalFilename()
        );
    }
    
    public void delete(User user, Long fileId) {
        FileMetadata meta = metadataService.findOwnedById(user, fileId);
        storageService.delete(meta.getStorageKey());
        metadataService.deleteById(fileId);
    }
    
    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}

