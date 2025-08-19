package org.ddamme.controller;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.dto.FileListResponse;
import org.ddamme.dto.FileUploadResponse;
import org.ddamme.dto.PagedFileResponse;
import org.ddamme.dto.DownloadUrlResponse;
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
import java.util.stream.Collectors;
import java.util.Map;
import org.ddamme.logging.AuditLogger;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final MetadataService metadataService;
    private final StorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file to storage and create metadata")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal User currentUser) {

        log.info("Received file upload request - filename: {}, size: {}, contentType: {}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        String storageKey = storageService.upload(file);

        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .size(file.getSize())
                .contentType(file.getContentType())
                .user(currentUser)
                .build();

        FileMetadata savedMetadata = metadataService.save(metadata);

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

        AuditLogger.log("file_upload", Map.of(
                "user", currentUser.getUsername(),
                "file", savedMetadata.getOriginalFilename(),
                "id", savedMetadata.getId(),
                "size", savedMetadata.getSize()
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "Generate a presigned download URL for your file")
    public ResponseEntity<DownloadUrlResponse> downloadFile(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        FileMetadata metadata = metadataService.findOwnedById(currentUser, id);

        String downloadUrl = storageService.generatePresignedDownloadUrl(metadata.getStorageKey());

        AuditLogger.log("file_download_url", Map.of(
                "user", currentUser.getUsername(),
                "id", metadata.getId(),
                "storageKey", metadata.getStorageKey()
        ));

        return ResponseEntity.ok(DownloadUrlResponse.builder().downloadUrl(downloadUrl).build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete your file")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        FileMetadata metadata = metadataService.findOwnedById(currentUser, id);

        storageService.delete(metadata.getStorageKey());

        metadataService.deleteById(id);

        AuditLogger.log("file_delete", Map.of(
                "user", currentUser.getUsername(),
                "id", id,
                "storageKey", metadata.getStorageKey()
        ));

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List your files (paginated)")
    public ResponseEntity<PagedFileResponse> getUserFiles(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        
        Page<FileMetadata> userFilesPage = metadataService.findByUser(currentUser, pageable);
        
        List<FileListResponse> files = userFilesPage.getContent().stream()
                .map(file -> FileListResponse.builder()
                        .id(file.getId())
                        .originalFilename(file.getOriginalFilename())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .uploadTimestamp(file.getUploadTimestamp())
                        .build())
                .collect(Collectors.toList());
        
        PagedFileResponse response = PagedFileResponse.builder()
                .files(files)
                .currentPage(userFilesPage.getNumber())
                .totalPages(userFilesPage.getTotalPages())
                .totalElements(userFilesPage.getTotalElements())
                .hasNext(userFilesPage.hasNext())
                .hasPrevious(userFilesPage.hasPrevious())
                .build();
        
        return ResponseEntity.ok(response);
    }
} 