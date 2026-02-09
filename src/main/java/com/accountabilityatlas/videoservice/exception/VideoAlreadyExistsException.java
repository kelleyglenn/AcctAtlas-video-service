package com.accountabilityatlas.videoservice.exception;

public class VideoAlreadyExistsException extends RuntimeException {

  public VideoAlreadyExistsException(String youtubeId) {
    super("Video already exists with YouTube ID: " + youtubeId);
  }
}
