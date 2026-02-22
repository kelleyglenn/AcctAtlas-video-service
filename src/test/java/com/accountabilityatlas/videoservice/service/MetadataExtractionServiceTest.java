package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.config.AnthropicProperties;
import com.accountabilityatlas.videoservice.exception.MetadataExtractionException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MetadataExtractionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MessageService mockMessageService;
  private MetadataExtractionService service;

  @BeforeEach
  void setUp() {
    AnthropicProperties properties = new AnthropicProperties();
    properties.setApiKey("test-api-key");
    properties.setModel("claude-sonnet-4-6");
    properties.setMaxTokens(4096);
    properties.setTimeout(Duration.ofSeconds(60));

    service = new MetadataExtractionService(properties, objectMapper);

    AnthropicClient mockClient = mock(AnthropicClient.class);
    mockMessageService = mock(MessageService.class);
    when(mockClient.messages()).thenReturn(mockMessageService);

    ReflectionTestUtils.setField(service, "client", mockClient);
  }

  @Test
  void extract_noApiKey_throwsMetadataExtractionException() {
    AnthropicProperties properties = new AnthropicProperties();
    properties.setApiKey("");
    MetadataExtractionService noKeyService =
        new MetadataExtractionService(properties, objectMapper);

    Throwable thrown = catchThrowable(() -> noKeyService.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void extract_nullApiKey_throwsMetadataExtractionException() {
    AnthropicProperties properties = new AnthropicProperties();
    properties.setApiKey(null);
    MetadataExtractionService noKeyService =
        new MetadataExtractionService(properties, objectMapper);

    Throwable thrown = catchThrowable(() -> noKeyService.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void extract_validResponse_returnsParsedResult() {
    String json =
        """
        {
          "amendments": ["FIRST", "FOURTH"],
          "participants": ["POLICE", "CITIZEN"],
          "videoDate": "2024-03-15",
          "location": {
            "name": "Springfield City Hall",
            "city": "Springfield",
            "state": "IL",
            "latitude": 39.7817,
            "longitude": -89.6501
          },
          "confidence": {
            "amendments": 0.9,
            "participants": 0.85,
            "videoDate": 0.6,
            "location": 0.8
          }
        }""";

    stubMessageResponse(json);

    MetadataExtractionService.ExtractionResult result =
        service.extract("First Amendment Audit", "Auditing city hall in Springfield", null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FIRST", "FOURTH");
    assertThat(result.participants()).containsExactly("POLICE", "CITIZEN");
    assertThat(result.videoDate()).isEqualTo("2024-03-15");
    assertThat(result.location()).isNotNull();
    assertThat(result.location().name()).isEqualTo("Springfield City Hall");
    assertThat(result.location().city()).isEqualTo("Springfield");
    assertThat(result.location().state()).isEqualTo("IL");
    assertThat(result.location().latitude()).isEqualTo(39.7817);
    assertThat(result.location().longitude()).isEqualTo(-89.6501);
    assertThat(result.confidence()).isNotNull();
    assertThat(result.confidence().amendments()).isEqualTo(0.9);
    assertThat(result.confidence().participants()).isEqualTo(0.85);
    assertThat(result.confidence().videoDate()).isEqualTo(0.6);
    assertThat(result.confidence().location()).isEqualTo(0.8);
  }

  @Test
  void extract_responseWithCodeFences_stripsAndParses() {
    String json =
        """
        ```json
        {
          "amendments": ["FIRST"],
          "participants": ["POLICE"],
          "videoDate": null,
          "location": null,
          "confidence": {
            "amendments": 0.95,
            "participants": 0.9,
            "videoDate": null,
            "location": null
          }
        }
        ```""";

    stubMessageResponse(json);

    MetadataExtractionService.ExtractionResult result =
        service.extract("Police Encounter", "Recording the police", null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FIRST");
    assertThat(result.participants()).containsExactly("POLICE");
    assertThat(result.videoDate()).isNull();
    assertThat(result.location()).isNull();
    assertThat(result.confidence()).isNotNull();
    assertThat(result.confidence().amendments()).isEqualTo(0.95);
  }

  @Test
  void extract_responseWithCodeFencesNoLanguage_stripsAndParses() {
    String json =
        """
        ```
        {
          "amendments": ["FOURTH"],
          "participants": ["CITIZEN"],
          "videoDate": "2024-01-01",
          "location": null,
          "confidence": {
            "amendments": 0.7,
            "participants": 0.8,
            "videoDate": 0.5,
            "location": null
          }
        }
        ```""";

    stubMessageResponse(json);

    MetadataExtractionService.ExtractionResult result =
        service.extract("Fourth Amendment Test", "Description", null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FOURTH");
  }

  @Test
  void extract_responseWithXmlThinkingThenJson_parsesJsonFromEnd() {
    String response =
        """
        <evidence_extraction>
        The title mentions "First Amendment Audit" and "City Hall".
        The description mentions "police arrived" and "Springfield, IL".
        </evidence_extraction>

        <amendment_analysis>
        FIRST amendment is relevant due to "First Amendment Audit".
        </amendment_analysis>

        <amendment_validation>
        FIRST: VALID - content is about First Amendment auditing
        </amendment_validation>

        <date_processing>
        No date information found. videoDate should be null.
        </date_processing>

        <location_processing>
        City Hall in Springfield, IL. No coordinates found.
        </location_processing>

        <json_construction>
        amendments: ["FIRST"]
        participants: ["POLICE"]
        videoDate: null
        location: { name: "City Hall", city: "Springfield", state: "IL" }
        confidence: amendments 0.95, participants 0.9, videoDate null, location 0.85
        </json_construction>

        {
          "amendments": ["FIRST"],
          "participants": ["POLICE"],
          "videoDate": null,
          "location": {
            "name": "City Hall",
            "city": "Springfield",
            "state": "IL",
            "latitude": null,
            "longitude": null
          },
          "confidence": {
            "amendments": 0.95,
            "participants": 0.9,
            "videoDate": null,
            "location": 0.85
          }
        }""";

    stubMessageResponse(response);

    MetadataExtractionService.ExtractionResult result =
        service.extract("First Amendment Audit - City Hall", "Police arrived in Springfield", null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FIRST");
    assertThat(result.participants()).containsExactly("POLICE");
    assertThat(result.videoDate()).isNull();
    assertThat(result.location()).isNotNull();
    assertThat(result.location().name()).isEqualTo("City Hall");
    assertThat(result.location().city()).isEqualTo("Springfield");
    assertThat(result.location().state()).isEqualTo("IL");
    assertThat(result.location().latitude()).isNull();
    assertThat(result.location().longitude()).isNull();
    assertThat(result.confidence().amendments()).isEqualTo(0.95);
    assertThat(result.confidence().location()).isEqualTo(0.85);
  }

  @Test
  void extract_clientThrowsException_throwsMetadataExtractionException() {
    when(mockMessageService.create(any(MessageCreateParams.class)))
        .thenThrow(new RuntimeException("API connection failed"));

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("AI metadata extraction failed")
        .hasMessageContaining("API connection failed")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void extract_noTextContent_throwsMetadataExtractionException() {
    Message emptyMessage = mock(Message.class);
    when(emptyMessage.content()).thenReturn(List.of());
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(emptyMessage);

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("No text content in AI response");
  }

  @Test
  void extract_nonTextContentOnly_throwsMetadataExtractionException() {
    ContentBlock nonTextBlock = mock(ContentBlock.class);
    when(nonTextBlock.isText()).thenReturn(false);

    Message message = mock(Message.class);
    when(message.content()).thenReturn(List.of(nonTextBlock));
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("No text content in AI response");
  }

  @Test
  void extract_invalidJson_throwsMetadataExtractionException() {
    stubMessageResponse("this is not valid json");

    Throwable thrown = catchThrowable(() -> service.extract("Title", "Description", null));

    assertThat(thrown)
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("AI metadata extraction failed");
  }

  @Test
  void extract_nullDescription_sendsEmptyString() {
    String json =
        """
        {
          "amendments": ["FIRST"],
          "participants": ["POLICE"],
          "videoDate": null,
          "location": null,
          "confidence": {
            "amendments": 0.8,
            "participants": 0.8,
            "videoDate": null,
            "location": null
          }
        }""";

    stubMessageResponse(json);

    MetadataExtractionService.ExtractionResult result = service.extract("Title", null, null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FIRST");
  }

  @Test
  void extract_responseWithUnknownFields_ignoresThemGracefully() {
    String json =
        """
        {
          "amendments": ["FIRST"],
          "participants": ["POLICE"],
          "videoDate": "2024-06-01",
          "location": null,
          "confidence": {
            "amendments": 0.9,
            "participants": 0.85,
            "videoDate": 0.6,
            "location": null
          },
          "unknownField": "should be ignored"
        }""";

    stubMessageResponse(json);

    MetadataExtractionService.ExtractionResult result =
        service.extract("Title", "Description", null);

    assertThat(result).isNotNull();
    assertThat(result.amendments()).containsExactly("FIRST");
  }

  @Test
  void metadataExtractionException_messageOnlyConstructor() {
    MetadataExtractionException ex = new MetadataExtractionException("test message");

    assertThat(ex.getMessage()).isEqualTo("test message");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void metadataExtractionException_messageAndCauseConstructor() {
    RuntimeException cause = new RuntimeException("root cause");
    MetadataExtractionException ex = new MetadataExtractionException("test message", cause);

    assertThat(ex.getMessage()).isEqualTo("test message");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  /**
   * Stubs the mock message service to return a Message containing the given text. Builds all mock
   * objects first to avoid Mockito's "unfinished stubbing" detection issues.
   */
  private void stubMessageResponse(String text) {
    TextBlock textBlock = mock(TextBlock.class);
    when(textBlock.text()).thenReturn(text);

    ContentBlock contentBlock = mock(ContentBlock.class);
    when(contentBlock.isText()).thenReturn(true);
    when(contentBlock.asText()).thenReturn(textBlock);
    when(contentBlock.text()).thenReturn(Optional.of(textBlock));

    Message message = mock(Message.class);
    when(message.content()).thenReturn(List.of(contentBlock));

    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);
  }
}
