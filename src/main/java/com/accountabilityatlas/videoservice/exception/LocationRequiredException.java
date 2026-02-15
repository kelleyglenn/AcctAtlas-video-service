package com.accountabilityatlas.videoservice.exception;

public class LocationRequiredException extends RuntimeException {

  public LocationRequiredException() {
    super("A location is required when submitting a video");
  }
}
