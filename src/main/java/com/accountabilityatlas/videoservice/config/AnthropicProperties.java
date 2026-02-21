package com.accountabilityatlas.videoservice.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.anthropic")
@Getter
@Setter
public class AnthropicProperties {

  private String apiKey;
  private String model = "claude-haiku-4-5-20251001";
  private int maxTokens = 1024;
  private Duration timeout = Duration.ofSeconds(30);
}
