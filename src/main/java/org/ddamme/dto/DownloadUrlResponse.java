package org.ddamme.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DownloadUrlResponse {
  private String downloadUrl;
}
