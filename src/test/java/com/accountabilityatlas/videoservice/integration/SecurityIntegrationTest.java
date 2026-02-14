package com.accountabilityatlas.videoservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.UUID;
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

  @MockitoBean private SqsTemplate sqsTemplate;

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

  /** GET /actuator/health should return 200 without any auth. */
  @Test
  void actuatorHealth_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
