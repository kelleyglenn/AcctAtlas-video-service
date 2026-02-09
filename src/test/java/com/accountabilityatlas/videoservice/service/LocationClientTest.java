package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class LocationClientTest {

  @Test
  void getLocation_returnsSummary() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
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
      server.start();

      LocationClient client = new LocationClient(WebClient.builder(), server.url("/").toString());

      Optional<LocationClient.LocationSummary> result =
          client.getLocation(UUID.fromString("00000000-0000-0000-0000-000000000001"));

      assertThat(result).isPresent();
      assertThat(result.get().displayName()).isEqualTo("Display");
    }
  }

  @Test
  void getLocation_notFound_returnsEmpty() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(404));
      server.start();

      LocationClient client = new LocationClient(WebClient.builder(), server.url("/").toString());

      Optional<LocationClient.LocationSummary> result = client.getLocation(UUID.randomUUID());

      assertThat(result).isEmpty();
    }
  }
}
