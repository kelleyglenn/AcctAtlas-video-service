package com.accountabilityatlas.videoservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class LocationClient {

  private final WebClient webClient;

  public LocationClient(
      WebClient.Builder builder, @Value("${app.location-service.base-url}") String baseUrl) {
    this.webClient = builder.baseUrl(baseUrl).build();
  }

  public record LocationSummary(
      UUID id, String displayName, String city, String state, Coordinates coordinates) {}

  public record Coordinates(double latitude, double longitude) {}

  public Optional<LocationSummary> getLocation(UUID locationId) {
    try {
      return Optional.ofNullable(
          webClient
              .get()
              .uri("/locations/{id}", locationId)
              .retrieve()
              .bodyToMono(LocationSummary.class)
              .block());
    } catch (WebClientResponseException.NotFound e) {
      return Optional.empty();
    }
  }

  public void notifyVideoApproved(List<UUID> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return;
    }
    try {
      webClient
          .post()
          .uri("/internal/locations/video-approved")
          .bodyValue(new LocationIdsRequest(locationIds))
          .retrieve()
          .toBodilessEntity()
          .block();
    } catch (Exception e) {
      log.warn("Failed to notify location-service of video approval: {}", e.getMessage());
    }
  }

  public void notifyVideoRemoved(List<UUID> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return;
    }
    try {
      webClient
          .post()
          .uri("/internal/locations/video-removed")
          .bodyValue(new LocationIdsRequest(locationIds))
          .retrieve()
          .toBodilessEntity()
          .block();
    } catch (Exception e) {
      log.warn("Failed to notify location-service of video removal: {}", e.getMessage());
    }
  }

  private record LocationIdsRequest(List<UUID> locationIds) {}
}
