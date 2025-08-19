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
public class FileListResponse {
    private Long id;
    private String originalFilename;
    private long size;
    private String contentType;
    private Instant uploadTimestamp;
}