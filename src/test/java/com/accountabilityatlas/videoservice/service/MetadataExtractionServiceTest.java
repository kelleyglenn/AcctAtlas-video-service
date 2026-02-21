package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.accountabilityatlas.videoservice.config.AnthropicProperties;
import com.accountabilityatlas.videoservice.exception.MetadataExtractionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MetadataExtractionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void extract_noApiKey_throwsMetadataExtractionException() {
    AnthropicProperties properties = new AnthropicProperties();
    properties.setApiKey("");
    MetadataExtractionService service = new MetadataExtractionService(properties, objectMapper);

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description"));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void extract_nullApiKey_throwsMetadataExtractionException() {
    AnthropicProperties properties = new AnthropicProperties();
    properties.setApiKey(null);
    MetadataExtractionService service = new MetadataExtractionService(properties, objectMapper);

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description"));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("not configured");
  }
}
