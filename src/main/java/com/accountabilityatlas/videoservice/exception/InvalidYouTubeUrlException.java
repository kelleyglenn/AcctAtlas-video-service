package com.accountabilityatlas.videoservice.exception;

public class InvalidYouTubeUrlException extends RuntimeException {

  public InvalidYouTubeUrlException(String url) {
    super("Invalid YouTube URL: " + url);
  }
}
