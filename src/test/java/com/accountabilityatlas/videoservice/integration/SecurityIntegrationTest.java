package com.accountabilityatlas.videoservice.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountabilityatlas.videoservice.service.LocationClient;
import com.accountabilityatlas.videoservice.service.YouTubeService;
import com.accountabilityatlas.videoservice.service.YouTubeService.YouTubeMetadata;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Import(SecurityIntegrationTest.TestSecurityConfig.class)
class SecurityIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("video_service")
          .withUsername("video_service")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    // Disable Redis
    registry.add("spring.cache.type", () -> "none");
    registry.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration");
  }

  static final String TEST_USER_ID = UUID.randomUUID().toString();
  static final String TEST_LOCATION_ID = "660e8400-e29b-41d4-a716-446655440001";

  @TestConfiguration
  static class TestSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
      return token -> {
        if ("valid-token".equals(token)) {
          return Jwt.withTokenValue(token)
              .header("alg", "RS256")
              .subject(TEST_USER_ID)
              .claim("trustTier", "TRUSTED")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(300))
              .build();
        }
        throw new BadJwtException("Invalid token");
      };
    }
  }

  @Autowired private MockMvc mockMvc;

  @SuppressWarnings("UnusedVariable")
  @MockitoBean
  private SqsTemplate sqsTemplate;

  @MockitoBean private YouTubeService youTubeService;

  @MockitoBean private LocationClient locationClient;

  @BeforeEach
  void setUpMocks() {
    when(youTubeService.extractVideoId(anyString())).thenReturn("dQw4w9WgXcQ");
    when(youTubeService.fetchMetadata("dQw4w9WgXcQ"))
        .thenReturn(
            new YouTubeMetadata(
                "dQw4w9WgXcQ",
                "Test Video",
                "Test Description",
                "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
                212,
                "channel-123",
                "Test Channel",
                Instant.parse("2024-01-01T00:00:00Z"),
                null));
    when(locationClient.getLocation(any()))
        .thenReturn(
            Optional.of(
                new LocationClient.LocationSummary(
                    UUID.fromString(TEST_LOCATION_ID),
                    "Test Location",
                    "Test City",
                    "TS",
                    new LocationClient.Coordinates(40.0, -74.0))));
  }

  /** GET /videos without any auth header should return 200 (public endpoint). */
  @Test
  void getVideos_withoutAuth_returns200() throws Exception {
    mockMvc.perform(get("/videos")).andExpect(status().isOk());
  }

  /**
   * GET /videos with an invalid Bearer token should still return 200. The lenient filter treats
   * invalid tokens as anonymous rather than returning 401.
   */
  @Test
  void getVideos_withInvalidToken_returns200() throws Exception {
    mockMvc
        .perform(get("/videos").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isOk());
  }

  /** GET /videos with a valid Bearer token should return 200. */
  @Test
  void getVideos_withValidToken_returns200() throws Exception {
    mockMvc
        .perform(get("/videos").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk());
  }

  private static final String VALID_CREATE_VIDEO_BODY =
      """
      {
        "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "amendments": ["FIRST"],
        "participants": ["POLICE"],
        "locationId": "%s"
      }
      """
          .formatted(TEST_LOCATION_ID);

  private static final String CREATE_VIDEO_BODY_WITHOUT_LOCATION =
      """
      {
        "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "amendments": ["FIRST"],
        "participants": ["POLICE"]
      }
      """;

  /**
   * POST /videos without auth should return 403. The request body passes validation, so the
   * controller's requireCurrentUserId() is reached and throws UnauthorizedException which the
   * GlobalExceptionHandler maps to 403.
   */
  @Test
  void postVideos_withoutAuth_returns403() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_VIDEO_BODY))
        .andExpect(status().isForbidden());
  }

  /**
   * POST /videos with an invalid token should return 403. The lenient filter treats the invalid
   * token as anonymous, then the controller rejects the unauthenticated request.
   */
  @Test
  void postVideos_withInvalidToken_returns403() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_VIDEO_BODY))
        .andExpect(status().isForbidden());
  }

  /**
   * POST /videos with a valid token should create the video. The authenticated user's identity is
   * extracted from the JWT and the video is persisted with PENDING status.
   */
  @Test
  void postVideos_withValidToken_createsVideo() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_VIDEO_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.youtubeId").value("dQw4w9WgXcQ"))
        .andExpect(jsonPath("$.title").value("Test Video"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.submittedBy").value(TEST_USER_ID));
  }

  /**
   * POST /videos with an empty JSON body should return 400 with a VALIDATION_ERROR code. The
   * MethodArgumentNotValidException is caught by GlobalExceptionHandler because required fields are
   * missing.
   */
  @Test
  void postVideos_emptyBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  /**
   * POST /videos with empty amendments array should return 400. The Size(min=1) constraint on the
   * amendments field triggers MethodArgumentNotValidException.
   */
  @Test
  void postVideos_emptyAmendments_returns400() throws Exception {
    // Arrange
    String body =
        """
        {
          "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "amendments": [],
          "participants": ["POLICE"],
          "locationId": "%s"
        }
        """
            .formatted(TEST_LOCATION_ID);

    // Act & Assert
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.details[0].field").value("amendments"));
  }

  /**
   * POST /videos with empty participants array should return 400. The Size(min=1) constraint on the
   * participants field triggers MethodArgumentNotValidException.
   */
  @Test
  void postVideos_emptyParticipants_returns400() throws Exception {
    // Arrange
    String body =
        """
        {
          "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "amendments": ["FIRST"],
          "participants": [],
          "locationId": "%s"
        }
        """
            .formatted(TEST_LOCATION_ID);

    // Act & Assert
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details").isArray());
  }

  /**
   * POST /videos with a completely missing request body should return 400. The
   * HttpMessageNotReadableException is caught by GlobalExceptionHandler.
   */
  @Test
  void postVideos_missingBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  /**
   * POST /videos with a valid token but missing locationId should return 400 with a
   * VALIDATION_ERROR indicating locationId is required.
   */
  @Test
  void postVideos_missingLocationId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_VIDEO_BODY_WITHOUT_LOCATION))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[0].field").value("locationId"));
  }

  /** GET /actuator/health should return 200 without any auth. */
  @Test
  void actuatorHealth_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
