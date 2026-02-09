package com.accountabilityatlas.videoservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleVideoNotFound() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleVideoNotFound(new VideoNotFoundException(UUID.randomUUID()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
  }

  @Test
  void handleVideoAlreadyExists() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleVideoAlreadyExists(new VideoAlreadyExistsException("abc"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("VIDEO_EXISTS");
  }

  @Test
  void handleInvalidYouTubeUrl() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleInvalidYouTubeUrl(new InvalidYouTubeUrlException("bad"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_YOUTUBE_URL");
  }

  @Test
  void handleYouTubeVideoNotFound() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleYouTubeVideoNotFound(new YouTubeVideoNotFoundException("vid"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().code()).isEqualTo("YOUTUBE_VIDEO_UNAVAILABLE");
  }

  @Test
  void handleLocationNotFound() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleLocationNotFound(new LocationNotFoundException(UUID.randomUUID()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("LOCATION_NOT_FOUND");
  }

  @Test
  void handleLocationAlreadyLinked() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleLocationAlreadyLinked(
            new LocationAlreadyLinkedException(UUID.randomUUID(), UUID.randomUUID()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("LOCATION_ALREADY_LINKED");
  }

  @Test
  void handleUnauthorized() {
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleUnauthorized(new UnauthorizedException("no"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
  }

  @Test
  void handleValidation() {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
    bindingResult.addError(new FieldError("target", "field", "bad"));

    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().details()).hasSize(1);
  }
}
