package com.accountabilityatlas.videoservice.service;

import com.accountabilityatlas.videoservice.config.AnthropicProperties;
import com.accountabilityatlas.videoservice.exception.MetadataExtractionException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetadataExtractionService {

  private static final Logger log = LoggerFactory.getLogger(MetadataExtractionService.class);

  private static final String SYSTEM_PROMPT =
      "You are a metadata extraction assistant for AccountabilityAtlas, a platform that catalogs "
          + "videos of encounters between citizens and government/law enforcement in the United "
          + "States, focusing on constitutional rights (especially First Amendment audits).\n\n"
          + "Given a YouTube video's title and description, extract structured metadata about the "
          + "video. Respond ONLY with a JSON object — no markdown fences, no explanation.\n\n"
          + "The JSON object must have this structure:\n"
          + "{\n"
          + "  \"amendments\": [\"FIRST\", \"FOURTH\"],\n"
          + "  \"participants\": [\"POLICE\", \"CITIZEN\"],\n"
          + "  \"videoDate\": \"2024-03-15\",\n"
          + "  \"location\": {\n"
          + "    \"name\": \"Springfield City Hall\",\n"
          + "    \"city\": \"Springfield\",\n"
          + "    \"state\": \"IL\",\n"
          + "    \"latitude\": 39.7817,\n"
          + "    \"longitude\": -89.6501\n"
          + "  },\n"
          + "  \"confidence\": {\n"
          + "    \"amendments\": 0.9,\n"
          + "    \"participants\": 0.85,\n"
          + "    \"videoDate\": 0.6,\n"
          + "    \"location\": 0.8\n"
          + "  }\n"
          + "}\n\n"
          + "Valid amendment values: FIRST, SECOND, FOURTH, FIFTH, FOURTEENTH\n"
          + "Valid participant values: POLICE, GOVERNMENT, BUSINESS, CITIZEN, SECURITY\n"
          + "videoDate should be in YYYY-MM-DD format, or null if not determinable.\n"
          + "location should be null if not determinable.\n"
          + "confidence scores should be between 0.0 and 1.0.";

  private final AnthropicClient client;
  private final AnthropicProperties properties;
  private final ObjectMapper objectMapper;

  public MetadataExtractionService(AnthropicProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      this.client = null;
    } else {
      this.client =
          AnthropicOkHttpClient.builder()
              .apiKey(properties.getApiKey())
              .timeout(properties.getTimeout())
              .build();
    }
  }

  @CircuitBreaker(name = "anthropic")
  @Retry(name = "anthropic")
  public ExtractionResult extract(String title, String description) {
    if (client == null) {
      throw new MetadataExtractionException(
          "AI extraction is not configured. Set the ANTHROPIC_API_KEY environment variable.");
    }

    String userMessage =
        "Extract metadata from this YouTube video:\n\nTitle: "
            + title
            + "\n\nDescription:\n"
            + (description != null ? description : "");

    try {
      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(properties.getModel())
              .maxTokens((long) properties.getMaxTokens())
              .system(SYSTEM_PROMPT)
              .addUserMessage(userMessage)
              .build();

      Message response = client.messages().create(params);

      String text = extractText(response);
      String json = stripCodeFences(text);

      return objectMapper.readValue(json, ExtractionResult.class);
    } catch (MetadataExtractionException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract metadata from video: {}", title, e);
      throw new MetadataExtractionException("AI metadata extraction failed: " + e.getMessage(), e);
    }
  }

  private String extractText(Message response) {
    for (ContentBlock block : response.content()) {
      if (block.isText()) {
        return block.asText().text();
      }
    }
    throw new MetadataExtractionException("No text content in AI response");
  }

  private String stripCodeFences(String text) {
    String stripped = text.strip();
    if (stripped.startsWith("```")) {
      int firstNewline = stripped.indexOf('\n');
      if (firstNewline < 0) {
        return stripped;
      }
      int lastFence = stripped.lastIndexOf("```");
      if (lastFence > firstNewline) {
        return stripped.substring(firstNewline + 1, lastFence).strip();
      }
    }
    return stripped;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractionResult(
      List<String> amendments,
      List<String> participants,
      String videoDate,
      LocationSuggestion location,
      ConfidenceScores confidence) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LocationSuggestion(
      String name, String city, String state, Double latitude, Double longitude) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConfidenceScores(
      Double amendments, Double participants, Double videoDate, Double location) {}
}
