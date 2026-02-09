package com.accountabilityatlas.videoservice.exception;

import java.util.UUID;

public class LocationAlreadyLinkedException extends RuntimeException {

  public LocationAlreadyLinkedException(UUID videoId, UUID locationId) {
    super("Location " + locationId + " is already linked to video " + videoId);
  }
}
