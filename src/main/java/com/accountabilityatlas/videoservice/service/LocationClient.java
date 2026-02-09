package com.accountabilityatlas.videoservice.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
}
