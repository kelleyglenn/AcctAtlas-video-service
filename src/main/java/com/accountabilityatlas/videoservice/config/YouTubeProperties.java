package com.accountabilityatlas.videoservice.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.youtube")
@Getter
@Setter
public class YouTubeProperties {

  private String apiKey;
  private String baseUrl = "https://www.googleapis.com/youtube/v3";
  private Duration cacheTtl = Duration.ofHours(24);
  private Duration timeout = Duration.ofSeconds(5);
}
