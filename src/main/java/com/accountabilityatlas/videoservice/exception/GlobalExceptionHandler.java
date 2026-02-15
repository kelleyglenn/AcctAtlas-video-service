package com.accountabilityatlas.videoservice.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(
      String code, String message, List<FieldError> details, String traceId) {}

  public record FieldError(String field, String message) {}

  @ExceptionHandler(VideoNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleVideoNotFound(VideoNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(VideoAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleVideoAlreadyExists(VideoAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ErrorResponse("VIDEO_EXISTS", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(InvalidYouTubeUrlException.class)
  public ResponseEntity<ErrorResponse> handleInvalidYouTubeUrl(InvalidYouTubeUrlException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "INVALID_YOUTUBE_URL", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(YouTubeVideoNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleYouTubeVideoNotFound(
      YouTubeVideoNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            new ErrorResponse(
                "YOUTUBE_VIDEO_UNAVAILABLE", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(LocationNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleLocationNotFound(LocationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            new ErrorResponse(
                "LOCATION_NOT_FOUND", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(LocationAlreadyLinkedException.class)
  public ResponseEntity<ErrorResponse> handleLocationAlreadyLinked(
      LocationAlreadyLinkedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ErrorResponse(
                "LOCATION_ALREADY_LINKED", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("FORBIDDEN", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<FieldError> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                details,
                UUID.randomUUID().toString()));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    List<FieldError> details =
        ex.getConstraintViolations().stream()
            .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                details,
                UUID.randomUUID().toString()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "VALIDATION_ERROR",
                "Malformed or missing request body",
                null,
                UUID.randomUUID().toString()));
  }
}
