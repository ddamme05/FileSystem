package org.ddamme.controller;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.exception.AccessDeniedException;
import org.ddamme.service.MetadataService;
import org.ddamme.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final MetadataService metadataService;
    private final StorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Authentication authentication) {
        // 1. Get the current authenticated user
        User currentUser = (User) authentication.getPrincipal();
        
        // 2. Upload the file to storage and get the storage key
        String storageKey = storageService.upload(file);
        
        // 3. Create metadata record with user association
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .size(file.getSize())
                .contentType(file.getContentType())
                .user(currentUser)
                .build();
        
        // 4. Save metadata to database
        FileMetadata savedMetadata = metadataService.save(metadata);
        
        // 5. Return the saved metadata
        return ResponseEntity.ok(savedMetadata);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable Long id, Authentication authentication) {
        // 1. Get the current authenticated user
        User currentUser = (User) authentication.getPrincipal();
        
        // 2. Get metadata from database
        FileMetadata metadata = metadataService.findById(id);
        
        // 3. Verify user owns this file
        if (!metadata.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only access your own files");
        }

        // 4. Generate presigned download URL
        String downloadUrl = storageService.generatePresignedDownloadUrl(metadata.getStorageKey());

        // 5. Return the URL
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, Authentication authentication) {
        // 1. Get the current authenticated user
        User currentUser = (User) authentication.getPrincipal();
        
        // 2. Get metadata from database
        FileMetadata metadata = metadataService.findById(id);
        
        // 3. Verify user owns this file
        if (!metadata.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only delete your own files");
        }

        // 4. Delete file from storage
        storageService.delete(metadata.getStorageKey());

        // 5. Delete metadata record from database
        metadataService.deleteById(id);

        // 6. Return 204 No Content
        return ResponseEntity.noContent().build();
    }
} 