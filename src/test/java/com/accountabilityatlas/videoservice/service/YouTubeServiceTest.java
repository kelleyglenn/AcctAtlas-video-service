package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountabilityatlas.videoservice.config.YouTubeProperties;
import com.accountabilityatlas.videoservice.exception.InvalidYouTubeUrlException;
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
  void extractVideoId_standardUrl() {
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    assertThat(youTubeService.extractVideoId(url)).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_shortUrl() {
    String url = "https://youtu.be/dQw4w9WgXcQ";
    assertThat(youTubeService.extractVideoId(url)).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_embedUrl() {
    String url = "https://www.youtube.com/embed/dQw4w9WgXcQ";
    assertThat(youTubeService.extractVideoId(url)).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_withQueryParams() {
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120";
    assertThat(youTubeService.extractVideoId(url)).isEqualTo("dQw4w9WgXcQ");
  }

  @Test
  void extractVideoId_invalidUrl_throws() {
    String url = "https://vimeo.com/123456";
    assertThatThrownBy(() -> youTubeService.extractVideoId(url))
        .isInstanceOf(InvalidYouTubeUrlException.class)
        .hasMessageContaining("Invalid YouTube URL");
  }

  @Test
  void extractVideoId_nullUrl_throws() {
    assertThatThrownBy(() -> youTubeService.extractVideoId(null))
        .isInstanceOf(InvalidYouTubeUrlException.class);
  }

  @Test
  void extractVideoId_blankUrl_throws() {
    assertThatThrownBy(() -> youTubeService.extractVideoId("  "))
        .isInstanceOf(InvalidYouTubeUrlException.class);
  }
}
