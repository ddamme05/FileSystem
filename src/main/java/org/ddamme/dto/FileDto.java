package org.ddamme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ddamme.database.model.FileMetadata;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileDto {
  private Long id;
  private String originalFilename;
  private long size;
  private String contentType;
  private Instant uploadTimestamp;
  private Instant updateTimestamp;

  public static FileDto from(FileMetadata metadata) {
    return FileDto.builder()
        .id(metadata.getId())
        .originalFilename(metadata.getOriginalFilename())
        .size(metadata.getSize())
        .contentType(metadata.getContentType())
        .uploadTimestamp(metadata.getUploadTimestamp())
        .updateTimestamp(metadata.getUpdateTimestamp())
        .build();
  }
}
