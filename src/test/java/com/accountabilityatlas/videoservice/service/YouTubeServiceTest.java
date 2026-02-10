package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.accountabilityatlas.videoservice.config.YouTubeProperties;
import com.accountabilityatlas.videoservice.exception.InvalidYouTubeUrlException;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class YouTubeServiceTest {

  private YouTubeService youTubeService;

  @BeforeEach
  void setUp() {
    YouTubeProperties properties = new YouTubeProperties();
    properties.setApiKey("test-key");
    youTubeService = new YouTubeService(WebClient.builder(), properties);
  }

  @Test
  void extractVideoId_standardUrl_returnsId() {
    // Arrange
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    // Act
    String result = youTubeService.extractVideoId(url);

    // Assert
    assertThat(result).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_shortUrl_returnsId() {
    // Arrange
    String url = "https://youtu.be/dQw4w9WgXcQ";

    // Act
    String result = youTubeService.extractVideoId(url);

    // Assert
    assertThat(result).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_embedUrl_returnsId() {
    // Arrange
    String url = "https://www.youtube.com/embed/dQw4w9WgXcQ";

    // Act
    String result = youTubeService.extractVideoId(url);

    // Assert
    assertThat(result).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_queryParams_returnsId() {
    // Arrange
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120";

    // Act
    String result = youTubeService.extractVideoId(url);

    // Assert
    assertThat(result).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_invalidUrl_throwsException() {
    // Arrange
    String url = "https://vimeo.com/123456";

    // Act
    Throwable thrown = catchThrowable(() -> youTubeService.extractVideoId(url));

    // Assert
    assertThat(thrown)
        .isInstanceOf(InvalidYouTubeUrlException.class)
        .hasMessageContaining("Invalid YouTube URL");
  }

  @Test
  void extractVideoId_nullUrl_throwsException() {
    // Arrange

    // Act
    Throwable thrown = catchThrowable(() -> youTubeService.extractVideoId(null));

    // Assert
    assertThat(thrown).isInstanceOf(InvalidYouTubeUrlException.class);
  }

  @Test
  void extractVideoId_blankUrl_throwsException() {
    // Arrange
    String url = "  ";

    // Act
    Throwable thrown = catchThrowable(() -> youTubeService.extractVideoId(url));

    // Assert
    assertThat(thrown).isInstanceOf(InvalidYouTubeUrlException.class);
  }

  @Test
  void fetchMetadata_validResponse_parsesMetadata() throws Exception {
    // Arrange
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody(
                  """
                  {
                    "items": [{
                      "snippet": {
                        "title": "Title",
                        "description": "Desc",
                        "channelId": "channel",
                        "channelTitle": "Channel Name",
                        "publishedAt": "2024-01-01T00:00:00Z",
                        "thumbnails": {
                          "high": { "url": "http://thumb" }
                        }
                      },
                      "contentDetails": { "duration": "PT2M" }
                    }]
                  }
                  """));

      server.start();

      YouTubeProperties properties = new YouTubeProperties();
      properties.setApiKey("test-key");
      properties.setBaseUrl(server.url("/").toString());
      YouTubeService service = new YouTubeService(WebClient.builder(), properties);

      // Act
      var metadata = service.fetchMetadata("abc123def45");

      // Assert
      assertThat(metadata.videoId()).isEqualTo("abc123def45");
      assertThat(metadata.title()).isEqualTo("Title");
      assertThat(metadata.thumbnailUrl()).isEqualTo("http://thumb");
      assertThat(metadata.durationSeconds()).isEqualTo(120);
      assertThat(metadata.publishedAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }
  }
}
