package com.accountabilityatlas.videoservice.exception;

import java.util.UUID;

public class LocationNotFoundException extends RuntimeException {

  public LocationNotFoundException(UUID locationId) {
    super("Location not found: " + locationId);
  }
}
