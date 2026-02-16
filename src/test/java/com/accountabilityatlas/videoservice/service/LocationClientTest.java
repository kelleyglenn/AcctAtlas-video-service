package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class LocationClientTest {

  private MockWebServer mockWebServer;
  private LocationClient locationClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = mockWebServer.url("/").toString();
    locationClient = new LocationClient(WebClient.builder(), baseUrl);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void getLocation_found_returnsSummary() {
    // Arrange
    mockWebServer.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "id": "00000000-0000-0000-0000-000000000001",
                  "displayName": "Display",
                  "city": "City",
                  "state": "State",
                  "coordinates": { "latitude": 1.0, "longitude": 2.0 }
                }
                """));

    // Act
    Optional<LocationClient.LocationSummary> result =
        locationClient.getLocation(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().displayName()).isEqualTo("Display");
  }

  @Test
  void getLocation_notFound_returnsEmpty() {
    // Arrange
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    // Act
    Optional<LocationClient.LocationSummary> result = locationClient.getLocation(UUID.randomUUID());

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  void notifyVideoApproved_sendsPostRequest() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThatCode(() -> locationClient.notifyVideoApproved(List.of(UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  void notifyVideoRemoved_sendsPostRequest() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThatCode(() -> locationClient.notifyVideoRemoved(List.of(UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  void notifyVideoApproved_emptyList_doesNothing() {
    // No server response needed since no request should be made
    assertThatCode(() -> locationClient.notifyVideoApproved(List.of())).doesNotThrowAnyException();
  }

  @Test
  void notifyVideoApproved_serverError_doesNotThrow() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatCode(() -> locationClient.notifyVideoApproved(List.of(UUID.randomUUID())))
        .doesNotThrowAnyException();
  }
}
