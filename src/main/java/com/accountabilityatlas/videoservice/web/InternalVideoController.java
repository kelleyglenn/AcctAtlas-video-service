package com.accountabilityatlas.videoservice.web;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/videos")
@RequiredArgsConstructor
public class InternalVideoController {

  private final VideoService videoService;
  private final VideoLocationService videoLocationService;

  public record UpdateVideoRequest(
      Set<String> amendments, Set<String> participants, LocalDate videoDate) {}

  public record UpdateStatusRequest(String status) {}

  public record AddLocationRequest(UUID locationId, boolean isPrimary) {}

  @PutMapping("/{id}")
  public ResponseEntity<Void> updateVideo(
      @PathVariable UUID id, @RequestBody UpdateVideoRequest request) {
    Set<Amendment> amendments =
        request.amendments() != null
            ? request.amendments().stream().map(Amendment::valueOf).collect(Collectors.toSet())
            : null;
    Set<Participant> participants =
        request.participants() != null
            ? request.participants().stream().map(Participant::valueOf).collect(Collectors.toSet())
            : null;

    videoService.updateVideoInternal(id, amendments, participants, request.videoDate());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<Void> updateStatus(
      @PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
    VideoStatus status = VideoStatus.valueOf(request.status());
    videoService.updateVideoStatus(id, status, null);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/locations")
  public ResponseEntity<VideoLocation> addLocation(
      @PathVariable UUID id, @RequestBody AddLocationRequest request) {
    VideoLocation location =
        videoLocationService.addLocationInternal(id, request.locationId(), request.isPrimary());
    return ResponseEntity.status(HttpStatus.CREATED).body(location);
  }

  @DeleteMapping("/{id}/locations/{locationId}")
  public ResponseEntity<Void> removeLocation(@PathVariable UUID id, @PathVariable UUID locationId) {
    videoLocationService.removeLocationInternal(id, locationId);
    return ResponseEntity.noContent().build();
  }
}
