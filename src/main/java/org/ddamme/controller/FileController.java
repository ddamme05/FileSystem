package org.ddamme.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.dto.FileListResponse;
import org.ddamme.dto.FileUploadResponse;
import org.ddamme.dto.PagedFileResponse;
import org.ddamme.exception.AccessDeniedException;
import org.ddamme.service.MetadataService;
import org.ddamme.service.StorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Files")
public class FileController {

    private final MetadataService metadataService;
    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file to storage and create metadata")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal User currentUser) {

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

        // 5. Create clean response DTO (no sensitive user data)
        FileUploadResponse response = FileUploadResponse.builder()
                .id(savedMetadata.getId())
                .originalFilename(savedMetadata.getOriginalFilename())
                .storageKey(savedMetadata.getStorageKey())
                .size(savedMetadata.getSize())
                .contentType(savedMetadata.getContentType())
                .uploaderUsername(currentUser.getUsername())
                .uploadTimestamp(savedMetadata.getUploadTimestamp())
                .updateTimestamp(savedMetadata.getUpdateTimestamp())
                .build();

        // 6. Return clean response
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "Generate a presigned download URL for your file")
    public ResponseEntity<org.ddamme.dto.DownloadUrlResponse> downloadFile(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        
        // 2. Get metadata from database
        FileMetadata metadata = metadataService.findById(id);
        
        // 3. Verify user owns this file
        if (metadata.getUser() == null) {
            throw new AccessDeniedException("File has no owner - access denied");
        }
        if (!metadata.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only access your own files");
        }

        // 4. Generate presigned download URL
        String downloadUrl = storageService.generatePresignedDownloadUrl(metadata.getStorageKey());

        // 5. Return the URL
        return ResponseEntity.ok(org.ddamme.dto.DownloadUrlResponse.builder().downloadUrl(downloadUrl).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        
        // 2. Get metadata from database
        FileMetadata metadata = metadataService.findById(id);
        
        // 3. Verify user owns this file
        if (metadata.getUser() == null) {
            throw new AccessDeniedException("File has no owner - access denied");
        }
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

    @GetMapping
    public ResponseEntity<PagedFileResponse> getUserFiles(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 1. Create pageable request (page 0-based, size = items per page)
        Pageable pageable = PageRequest.of(page, size);
        
        // 2. Get paginated files for this user
        Page<FileMetadata> userFilesPage = metadataService.findByUser(currentUser, pageable);
        
        // 3. Convert to clean DTOs (no sensitive data)
        List<FileListResponse> files = userFilesPage.getContent().stream()
                .map(file -> FileListResponse.builder()
                        .id(file.getId())
                        .originalFilename(file.getOriginalFilename())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .uploadTimestamp(file.getUploadTimestamp())
                        .build())
                .collect(Collectors.toList());
        
        // 4. Build paginated response
        PagedFileResponse response = PagedFileResponse.builder()
                .files(files)
                .currentPage(userFilesPage.getNumber())
                .totalPages(userFilesPage.getTotalPages())
                .totalElements(userFilesPage.getTotalElements())
                .hasNext(userFilesPage.hasNext())
                .hasPrevious(userFilesPage.hasPrevious())
                .build();
        
        // 5. Return the paginated list
        return ResponseEntity.ok(response);
    }
} 