package com.accountabilityatlas.videoservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleVideoNotFound_exception_returnsNotFoundResponse() {
    // Arrange
    VideoNotFoundException exception = new VideoNotFoundException(UUID.randomUUID());

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleVideoNotFound(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
  }

  @Test
  void handleVideoAlreadyExists_exception_returnsConflictResponse() {
    // Arrange
    VideoAlreadyExistsException exception = new VideoAlreadyExistsException("abc");

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleVideoAlreadyExists(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("VIDEO_EXISTS");
  }

  @Test
  void handleInvalidYouTubeUrl_exception_returnsBadRequestResponse() {
    // Arrange
    InvalidYouTubeUrlException exception = new InvalidYouTubeUrlException("bad");

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleInvalidYouTubeUrl(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_YOUTUBE_URL");
  }

  @Test
  void handleYouTubeVideoNotFound_exception_returnsUnprocessableResponse() {
    // Arrange
    YouTubeVideoNotFoundException exception = new YouTubeVideoNotFoundException("vid");

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleYouTubeVideoNotFound(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().code()).isEqualTo("YOUTUBE_VIDEO_UNAVAILABLE");
  }

  @Test
  void handleLocationNotFound_exception_returnsNotFoundResponse() {
    // Arrange
    LocationNotFoundException exception = new LocationNotFoundException(UUID.randomUUID());

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleLocationNotFound(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("LOCATION_NOT_FOUND");
  }

  @Test
  void handleLocationAlreadyLinked_exception_returnsConflictResponse() {
    // Arrange
    LocationAlreadyLinkedException exception =
        new LocationAlreadyLinkedException(UUID.randomUUID(), UUID.randomUUID());

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleLocationAlreadyLinked(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("LOCATION_ALREADY_LINKED");
  }

  @Test
  void handleUnauthorized_exception_returnsForbiddenResponse() {
    // Arrange
    UnauthorizedException exception = new UnauthorizedException("no");

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleUnauthorized(exception);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
  }

  @Test
  void handleValidation_invalidArguments_returnsValidationDetails() {
    // Arrange
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
    bindingResult.addError(new FieldError("target", "field", "bad"));

    // Act
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleValidation(ex);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().details()).hasSize(1);
  }

  @Test
  void handleConstraintViolation_violations_returnsBadRequestWithDetails() {
    // Arrange
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
    Path path = org.mockito.Mockito.mock(Path.class);
    org.mockito.Mockito.when(path.toString()).thenReturn("amendments");
    org.mockito.Mockito.when(violation.getPropertyPath()).thenReturn(path);
    org.mockito.Mockito.when(violation.getMessage()).thenReturn("must not be empty");
    ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleConstraintViolation(ex);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().details()).hasSize(1);
    assertThat(response.getBody().details().getFirst().field()).isEqualTo("amendments");
  }

  @Test
  void handleMessageNotReadable_malformedBody_returnsBadRequest() {
    // Arrange
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException(
            "Could not read JSON", (org.springframework.http.HttpInputMessage) null);

    // Act
    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleMessageNotReadable(ex);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().message()).isEqualTo("Malformed or missing request body");
    assertThat(response.getBody().details()).isNull();
  }
}
