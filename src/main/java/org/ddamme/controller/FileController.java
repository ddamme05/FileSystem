package org.ddamme.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.dto.DownloadUrlResponse;
import org.ddamme.dto.FileDto;
import org.ddamme.dto.FileListResponse;
import org.ddamme.dto.PagedFileResponse;
import org.ddamme.logging.AuditLogger;
import org.ddamme.service.FileService;
import org.ddamme.service.MetadataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

  private final FileService fileService;
  private final MetadataService metadataService;

  private static final int MAX_PAGE_SIZE = 100;

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload a file to storage and create metadata")
  public ResponseEntity<FileDto> uploadFile(
      @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User currentUser) {

    FileMetadata savedMetadata = fileService.upload(currentUser, file);

    AuditLogger.log(
        "file_upload",
        Map.of(
            "user", currentUser.getUsername(),
            "fileId", savedMetadata.getId(),
            "filename", savedMetadata.getOriginalFilename(),
            "size", savedMetadata.getSize()));

    return ResponseEntity.ok(FileDto.from(savedMetadata));
  }

  @GetMapping("/download/{id}")
  @Operation(summary = "Redirect to presigned download URL for your file")
  public ResponseEntity<Void> downloadFile(
      @PathVariable Long id, @AuthenticationPrincipal User currentUser) {
    String downloadUrl = fileService.presignDownloadUrl(currentUser, id);

    AuditLogger.log("file_download_url", Map.of("user", currentUser.getUsername(), "fileId", id));

    return ResponseEntity.status(302)
        .header("Location", downloadUrl)
        .build();
  }

  @GetMapping("/download/{id}/redirect")
  @Operation(summary = "Generate a presigned download URL for your file (returns JSON)")
  public ResponseEntity<DownloadUrlResponse> downloadFileRedirect(
      @PathVariable Long id, @AuthenticationPrincipal User currentUser) {
    String downloadUrl = fileService.presignDownloadUrl(currentUser, id);

    AuditLogger.log("file_download_url", Map.of("user", currentUser.getUsername(), "fileId", id));

    return ResponseEntity.ok(DownloadUrlResponse.builder().downloadUrl(downloadUrl).build());
  }

  @GetMapping("/view/{id}/redirect")
  @Operation(summary = "Generate a presigned view URL for your file (inline Content-Disposition)")
  public ResponseEntity<DownloadUrlResponse> viewFile(
      @PathVariable Long id, @AuthenticationPrincipal User currentUser) {
    String viewUrl = fileService.presignViewUrl(currentUser, id);

    AuditLogger.log("file_view_url", Map.of("user", currentUser.getUsername(), "fileId", id));

    return ResponseEntity.ok(DownloadUrlResponse.builder().downloadUrl(viewUrl).build());
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete your file")
  public ResponseEntity<?> deleteFile(
      @PathVariable Long id, @AuthenticationPrincipal User currentUser) {
    fileService.delete(currentUser, id);

    AuditLogger.log("file_delete", Map.of("user", currentUser.getUsername(), "fileId", id));

    return ResponseEntity.noContent().build();
  }

  @GetMapping
  @Operation(summary = "List your files (paginated)")
  public ResponseEntity<PagedFileResponse> getUserFiles(
      @AuthenticationPrincipal User currentUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    log.debug("Fetching files for user: {} (ID: {})", currentUser.getUsername(), currentUser.getId());

    int clampedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    PageRequest pageable = PageRequest.of(page, clampedSize);

    Page<FileMetadata> userFilesPage = metadataService.findByUser(currentUser, pageable);
    
    log.debug("Found {} files for user {}", userFilesPage.getTotalElements(), currentUser.getUsername());

    List<FileListResponse> files =
        userFilesPage.getContent().stream()
            .map(
                file ->
                    FileListResponse.builder()
                        .id(file.getId())
                        .originalFilename(file.getOriginalFilename())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .uploadTimestamp(file.getUploadTimestamp())
                        .build())
            .collect(Collectors.toList());

    PagedFileResponse response =
        PagedFileResponse.builder()
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
