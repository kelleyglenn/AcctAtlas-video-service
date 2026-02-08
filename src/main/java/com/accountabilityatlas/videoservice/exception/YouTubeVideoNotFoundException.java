package com.accountabilityatlas.videoservice.exception;

public class YouTubeVideoNotFoundException extends RuntimeException {

  public YouTubeVideoNotFoundException(String videoId) {
    super("YouTube video not found: " + videoId);
  }
}
