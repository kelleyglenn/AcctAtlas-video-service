package com.accountabilityatlas.videoservice.service;

import com.accountabilityatlas.videoservice.config.AnthropicProperties;
import com.accountabilityatlas.videoservice.exception.MetadataExtractionException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
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

  // spotless:off
  private static final String USER_PROMPT_TEMPLATE =
      """
      You are a metadata extraction assistant for AccountabilityAtlas, a platform that catalogs \
      videos documenting encounters between citizens and government/law enforcement in the \
      United States, with a focus on constitutional rights (especially First Amendment audits).

      Here is the YouTube video information you need to analyze:

      <video_description>
      {{description}}
      </video_description>

      <video_title>
      {{title}}
      </video_title>

      <publication_date>
      {{published}}
      </publication_date>

      ## Your Task

      Extract structured metadata from this video and output it as a JSON object. You will identify:

      1. **Constitutional amendments** involved in the encounter
      2. **Types of participants** in the encounter (excluding the video publisher themselves)
      3. **Date** when the encounter occurred (not the publication date)
      4. **Location** where the encounter occurred
      5. **Confidence scores** for each category of extracted information

      ## Classification Categories

      ### Amendments

      Identify which constitutional amendments are relevant to this encounter. You may select \
      multiple amendments.

      **Valid values:**
      - **FIRST**: The encounter involves freedom of press, religion, assembly, speech, or the \
      right to petition the government and/or protest
      - **SECOND**: The encounter involves the right to bear firearms or other weapons
      - **FOURTH**: The encounter involves the right to be free from searches or seizures, \
      including issues about providing identification or requiring search warrants
      - **FIFTH**: The encounter involves the right to remain silent and not incriminate oneself
      - **FOURTEENTH**: The encounter involves citizenship rights for those born/naturalized in \
      the U.S., or guarantees of due process and equal protection under the law

      **CRITICAL CONSTRAINT for FOURTH, FIFTH, and FOURTEENTH:**
      You may ONLY select these amendments if:
      - POLICE or GOVERNMENT are among the participants, OR
      - These specific amendments are explicitly mentioned by name in the video title or description

      If you cannot identify any valid amendments after applying this constraint, default to FIRST \
      and assign an appropriately low confidence score.

      ### Participants

      Identify all types of participants involved in the encounter. You may select multiple \
      participant types. Do NOT categorize the video publisher themselves.

      **Valid values:**
      - **POLICE**: Law enforcement at any level (local, city, county, state, or federal)
      - **GOVERNMENT**: Government workers who are not law enforcement (e.g., mayor, city/county \
      clerk, district attorneys, public works employees)
      - **BUSINESS**: Owners or employees of private (non-government) businesses
      - **SECURITY**: Private security personnel (not law enforcement)
      - **CITIZEN**: Use this when an encounter participant is mentioned but their category cannot \
      otherwise be determined

      ### Video Date

      Extract the date when the encounter occurred (not the publication date).

      - Use format: YYYY-MM-DD
      - Set to null if you cannot determine the date from the title or description
      - If the title and description don't specify an exact date AND don't provide a relative date \
      (like "yesterday", "last Tuesday", or "five days ago"), set to null and assign a low \
      confidence score

      ### Location

      Extract location information where the encounter occurred.

      **Fields to extract:**
      - **name**: Location name such as a landmark (e.g., "Springfield City Hall") or street \
      address. See special instructions below.
      - **city**: City name
      - **state**: State abbreviation (e.g., "CA", "TX")
      - **latitude** and **longitude**: Set these to null UNLESS they are explicitly stated in \
      the video description. Do not calculate, assume, or look up coordinates.

      **Special instruction for location name:**
      If you find multiple potential location names in the evidence, select ONE using this \
      priority order:
      1. Street address (most specific)
      2. Specific landmark (e.g., "City Hall", "County Courthouse")
      3. General landmark (e.g., "Police Department", "Post Office")
      4. If no other distinguishing factors exist, choose the first one mentioned

      Set the entire location object to null if you cannot determine any location information.

      ### Confidence Scores

      For each major field (amendments, participants, videoDate, location), provide a confidence \
      score between 0.0 (no confidence) and 1.0 (complete confidence) indicating how certain you \
      are about your extraction.

      ## Required Output Format

      You must output ONLY a JSON object. Do NOT include markdown code fences (like ```json). Do \
      NOT include any explanatory text before or after the JSON.

      The JSON structure:

      ```json
      {
        "amendments": ["AMENDMENT_NAME_1", "AMENDMENT_NAME_2"],
        "participants": ["PARTICIPANT_TYPE_1", "PARTICIPANT_TYPE_2"],
        "videoDate": "YYYY-MM-DD or null",
        "location": {
          "name": "location name or null",
          "city": "city name or null",
          "state": "XX or null",
          "latitude": 0.0 or null,
          "longitude": 0.0 or null
        },
        "confidence": {
          "amendments": 0.0,
          "participants": 0.0,
          "videoDate": 0.0,
          "location": 0.0
        }
      }
      ```

      ## Processing Steps

      Before constructing your final JSON output, work through the following analytical steps:

      **Step 1: Extract Evidence**

      Wrap your work in <evidence_extraction> tags. Extract and quote relevant information from \
      the video title and description. It's OK for this section to be quite long.

      - Quote specific phrases that suggest constitutional concepts (e.g., "filming in public", \
      "refused to ID", "open carry", "detained")
      - Quote specific phrases that identify participant types (e.g., "officer", "deputy", \
      "city clerk", "security guard", "store manager")
      - Quote any dates mentioned (both absolute dates like "January 15, 2024" and relative dates \
      like "yesterday" or "last week")
      - Quote any location information (city names, state names, building names, street addresses)
      - Explicitly check: Are latitude and longitude coordinates stated in the description? If \
      yes, quote them exactly.

      **Step 2: Analyze Amendments**

      Wrap your work in <amendment_analysis> tags. Map the constitutional concepts to amendments:

      - For each constitutional concept you identified, determine which amendment(s) it relates to
      - List all potentially relevant amendments with your reasoning
      - Note which participant types you've identified so far

      **Step 3: Validate Amendments**

      Wrap your work in <amendment_validation> tags. Validate your amendment selections against \
      the critical constraint:

      - For each amendment you're considering (FIRST, SECOND, FOURTH, FIFTH, FOURTEENTH), \
      explicitly write "VALID" or "INVALID" next to it
      - For FOURTH, FIFTH, and FOURTEENTH specifically: Check if POLICE or GOVERNMENT are \
      participants, OR if these amendments are explicitly mentioned by name in the \
      title/description. Write out your reasoning for marking each as VALID or INVALID.
      - For FIRST and SECOND: These don't have the critical constraint, so explain why they are \
      VALID or INVALID based on the content alone
      - If all amendments are marked INVALID, note that you will default to FIRST with low \
      confidence

      **Step 4: Process Date**

      Wrap your work in <date_processing> tags to determine the video date:

      - Is there an absolute date in the title/description? If yes, convert it to YYYY-MM-DD format
      - Is there a relative date (e.g., "yesterday", "last Tuesday")? If yes, calculate the \
      actual date using the publication date as reference
      - If neither, note that videoDate should be null
      - Assess what your confidence score should be based on the specificity of date information

      **Step 5: Process Location**

      Wrap your work in <location_processing> tags to structure the location information:

      - List out ALL potential location names found in the evidence (landmarks, street addresses, \
      building names)
      - If multiple location names exist, apply the selection priority: street address > specific \
      landmark > general landmark > first mentioned. Explain your selection by evaluating each \
      candidate against these criteria.
      - Extract the city (if any)
      - Extract the state (if any)
      - For latitude/longitude: Set to null unless you explicitly found coordinates in Step 1
      - If no location information was found, note that location should be null
      - Assess what your confidence score should be based on the specificity of location information

      **Step 6: Construct JSON**

      Wrap your work in <json_construction> tags to build your JSON object step by step:

      - Finalize the amendments array (only VALID amendments from Step 3)
      - Finalize the participants array
      - Set the videoDate value
      - Structure the location object with the selected location name
      - Assign confidence scores for each field. For each of the four confidence scores \
      (amendments, participants, videoDate, location), write out explicit justification for \
      the specific numeric value you're assigning (e.g., "amendments: 0.85 because X, Y, \
      and Z").

      **Step 7: Output Final JSON**

      After completing all analytical steps, output only the final JSON object with no additional \
      text, no markdown formatting, and no code fences.""";
  // spotless:on

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
  public ExtractionResult extract(String title, String description, String publishedAt) {
    if (client == null) {
      throw new MetadataExtractionException(
          "AI extraction is not configured. Set the ANTHROPIC_API_KEY environment variable.");
    }

    String userMessage =
        USER_PROMPT_TEMPLATE
            .replace("{{title}}", title != null ? title : "")
            .replace("{{description}}", description != null ? description : "")
            .replace("{{published}}", publishedAt != null ? publishedAt : "unknown");

    log.debug("Extraction request for video: {}", title);
    log.debug("User message sent to Claude:\n{}", userMessage);

    try {
      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(properties.getModel())
              .maxTokens(properties.getMaxTokens())
              .addUserMessage(userMessage)
              .build();

      Message response = client.messages().create(params);

      String text = extractText(response);
      log.debug("Raw Claude response:\n{}", text);

      String json = extractJson(text);

      return objectMapper.readValue(json, ExtractionResult.class);
    } catch (JsonProcessingException e) {
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

  /**
   * Extracts the last top-level JSON object from the response text. The response may contain XML
   * thinking tags (evidence_extraction, amendment_analysis, etc.) followed by the final JSON
   * object. This method finds the last balanced {@code {...}} block in the response.
   */
  private String extractJson(String text) {
    String trimmed = text.strip();

    // Handle code fences if present
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      if (firstNewline >= 0) {
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence > firstNewline) {
          trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
        }
      }
    }

    // Find the last '}' and walk back to find its matching '{'
    int lastBrace = trimmed.lastIndexOf('}');
    if (lastBrace < 0) {
      return trimmed;
    }

    int depth = 0;
    for (int i = lastBrace; i >= 0; i--) {
      char c = trimmed.charAt(i);
      if (c == '}') {
        depth++;
      } else if (c == '{') {
        depth--;
        if (depth == 0) {
          return trimmed.substring(i, lastBrace + 1);
        }
      }
    }

    return trimmed;
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
