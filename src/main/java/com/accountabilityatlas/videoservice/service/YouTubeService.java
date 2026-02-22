package com.accountabilityatlas.videoservice.service;

import com.accountabilityatlas.videoservice.config.YouTubeProperties;
import com.accountabilityatlas.videoservice.exception.InvalidYouTubeUrlException;
import com.accountabilityatlas.videoservice.exception.YouTubeVideoNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class YouTubeService {

  private static final List<Pattern> YOUTUBE_PATTERNS =
      List.of(
          Pattern.compile("youtube\\.com/watch\\?v=([\\w-]{11})"),
          Pattern.compile("youtu\\.be/([\\w-]{11})"),
          Pattern.compile("youtube\\.com/embed/([\\w-]{11})"));

  private final WebClient webClient;
  private final YouTubeProperties properties;

  public YouTubeService(WebClient.Builder webClientBuilder, YouTubeProperties properties) {
    this.properties = properties;
    this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
  }

  public record YouTubeMetadata(
      String videoId,
      String title,
      String description,
      String thumbnailUrl,
      Integer durationSeconds,
      String channelId,
      String channelName,
      Instant publishedAt,
      String recordingDate)
      implements Serializable {}

  public String extractVideoId(String url) {
    if (url == null || url.isBlank()) {
      throw new InvalidYouTubeUrlException(url);
    }

    for (Pattern pattern : YOUTUBE_PATTERNS) {
      Matcher matcher = pattern.matcher(url);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    throw new InvalidYouTubeUrlException(url);
  }

  @Cacheable(value = "youtube-metadata", key = "#videoId")
  @CircuitBreaker(name = "youtube")
  @Retry(name = "youtube")
  public YouTubeMetadata fetchMetadata(String videoId) {
    try {
      var response =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/videos")
                          .queryParam("id", videoId)
                          .queryParam("key", properties.getApiKey())
                          .queryParam("part", "snippet,contentDetails,recordingDetails")
                          .build())
              .retrieve()
              .bodyToMono(YouTubeApiResponse.class)
              .block(properties.getTimeout());

      if (response == null || response.items() == null || response.items().isEmpty()) {
        throw new YouTubeVideoNotFoundException(videoId);
      }

      var item = response.items().getFirst();
      var snippet = item.snippet();
      var contentDetails = item.contentDetails();
      var recordingDetails = item.recordingDetails();

      return new YouTubeMetadata(
          videoId,
          snippet.title(),
          snippet.description(),
          selectBestThumbnail(snippet.thumbnails()),
          parseDuration(contentDetails.duration()),
          snippet.channelId(),
          snippet.channelTitle(),
          Instant.parse(snippet.publishedAt()),
          recordingDetails != null ? recordingDetails.recordingDate() : null);

    } catch (WebClientResponseException.NotFound e) {
      throw new YouTubeVideoNotFoundException(videoId);
    }
  }

  private String selectBestThumbnail(Thumbnails thumbnails) {
    if (thumbnails.maxres() != null) return thumbnails.maxres().url();
    if (thumbnails.high() != null) return thumbnails.high().url();
    if (thumbnails.medium() != null) return thumbnails.medium().url();
    if (thumbnails.standard() != null) return thumbnails.standard().url();
    return thumbnails.defaultThumb() != null ? thumbnails.defaultThumb().url() : null;
  }

  private Integer parseDuration(String isoDuration) {
    if (isoDuration == null) return null;
    try {
      return (int) Duration.parse(isoDuration).toSeconds();
    } catch (Exception e) {
      return null;
    }
  }

  record YouTubeApiResponse(List<VideoItem> items) {}

  record VideoItem(
      Snippet snippet, ContentDetails contentDetails, RecordingDetails recordingDetails) {}

  record Snippet(
      String title,
      String description,
      String channelId,
      String channelTitle,
      String publishedAt,
      Thumbnails thumbnails) {}

  record ContentDetails(String duration) {}

  record RecordingDetails(String recordingDate) {}

  record Thumbnails(
      Thumbnail defaultThumb,
      Thumbnail medium,
      Thumbnail high,
      Thumbnail standard,
      Thumbnail maxres) {}

  record Thumbnail(String url, int width, int height) {}
}
