package com.accountabilityatlas.videoservice.exception;

import java.util.UUID;

public class VideoNotFoundException extends RuntimeException {

  public VideoNotFoundException(UUID id) {
    super("Video not found: " + id);
  }
}
