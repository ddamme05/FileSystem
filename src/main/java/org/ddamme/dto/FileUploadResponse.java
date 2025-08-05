package org.ddamme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadResponse {
    private Long id;
    private String originalFilename;
    private String storageKey;
    private long size;
    private String contentType;
    private String uploaderUsername;
    private Instant uploadTimestamp;
    private Instant updateTimestamp;
}