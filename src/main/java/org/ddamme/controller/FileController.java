package org.ddamme.controller;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.service.MetadataService;
import org.ddamme.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final MetadataService metadataService;
    private final StorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        // 1. Upload the file to storage and get the storage key
        String storageKey = storageService.upload(file);
        
        // 2. Create metadata record
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .size(file.getSize())
                .contentType(file.getContentType())
                .build();
        
        // 3. Save metadata to database
        FileMetadata savedMetadata = metadataService.save(metadata);
        
        // 4. Return the saved metadata
        return ResponseEntity.ok(savedMetadata);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable Long id) {
        // 1. Get metadata from database
        Optional<FileMetadata> metadataOptional = metadataService.findById(id);
        
        if (metadataOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        FileMetadata metadata = metadataOptional.get();
        
        // 2. Generate presigned download URL
        String downloadUrl = storageService.generatePresignedDownloadUrl(metadata.getStorageKey());
        
        // 3. Return the URL
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        // 1. Get metadata from database
        Optional<FileMetadata> metadataOptional = metadataService.findById(id);
        
        if (metadataOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        FileMetadata metadata = metadataOptional.get();
        
        // 2. Delete file from storage
        storageService.delete(metadata.getStorageKey());
        
        // 3. Delete metadata record from database
        metadataService.deleteById(id);
        
        // 4. Return 204 No Content
        return ResponseEntity.noContent().build();
    }
} 