package com.accountabilityatlas.videoservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class LenientJwtAuthenticationFilterTest {

  @Mock private JwtDecoder jwtDecoder;
  @Mock private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilterInternal_validToken_setsAuthentication() throws Exception {
    // Arrange
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .claim("trustTier", "NEW")
            .build();
    when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    LenientJwtAuthenticationFilter filter = new LenientJwtAuthenticationFilter(jwtDecoder);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isInstanceOf(JwtAuthenticationToken.class);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_invalidToken_clearsContextAndContinues() throws Exception {
    // Arrange
    when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid token"));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer bad-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    LenientJwtAuthenticationFilter filter = new LenientJwtAuthenticationFilter(jwtDecoder);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_noAuthorizationHeader_continuesWithoutAuth() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    LenientJwtAuthenticationFilter filter = new LenientJwtAuthenticationFilter(jwtDecoder);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(jwtDecoder);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_nonBearerAuthorizationHeader_continuesWithoutAuth() throws Exception {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Basic abc123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    LenientJwtAuthenticationFilter filter = new LenientJwtAuthenticationFilter(jwtDecoder);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(jwtDecoder);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_validToken_setsCorrectPrincipal() throws Exception {
    // Arrange
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .claim("trustTier", "NEW")
            .build();
    when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    LenientJwtAuthenticationFilter filter = new LenientJwtAuthenticationFilter(jwtDecoder);

    // Act
    filter.doFilterInternal(request, response, filterChain);

    // Assert
    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getToken().getSubject()).isEqualTo("user-123");
    verify(filterChain).doFilter(request, response);
  }
}
