package org.ddamme.dto;

import lombok.*;

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
