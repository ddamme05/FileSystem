package org.ddamme.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ErrorResponse {

  private Instant timestamp;

  private int status;

  private String error;

  private String message;

  private String path;

  public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
    this.timestamp = timestamp;
    this.status = status;
    this.error = error;
    this.message = message;
    this.path = path;
  }
}
