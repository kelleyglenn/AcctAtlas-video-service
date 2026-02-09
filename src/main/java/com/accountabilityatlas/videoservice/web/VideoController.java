package com.accountabilityatlas.videoservice.web;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import com.accountabilityatlas.videoservice.web.api.VideosApi;
import com.accountabilityatlas.videoservice.web.model.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VideoController implements VideosApi {

  private final VideoService videoService;
  private final VideoLocationService videoLocationService;

  @Override
  public ResponseEntity<VideoDetail> getVideo(UUID id) {
    Video video = videoService.getVideo(id);
    return ResponseEntity.ok(toVideoDetail(video));
  }

  @Override
  public ResponseEntity<VideoListResponse> listVideos(
      com.accountabilityatlas.videoservice.web.model.VideoStatus status,
      List<com.accountabilityatlas.videoservice.web.model.Amendment> amendments,
      List<com.accountabilityatlas.videoservice.web.model.Participant> participants,
      UUID submittedBy,
      Integer page,
      Integer size,
      String sort,
      String direction) {

    PageRequest pageable =
        PageRequest.of(
            page != null ? page : 0,
            size != null ? size : 20,
            Sort.by(
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                sort != null ? sort : "createdAt"));

    VideoStatus domainStatus = status != null ? VideoStatus.valueOf(status.name()) : null;
    Page<Video> videos = videoService.listVideos(domainStatus, pageable);

    return ResponseEntity.ok(toVideoListResponse(videos));
  }

  @Override
  public ResponseEntity<VideoDetail> createVideo(
      CreateVideoRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());

    Video video =
        videoService.createVideo(
            request.getYoutubeUrl(),
            request.getAmendments().stream()
                .map(a -> Amendment.valueOf(a.name()))
                .collect(Collectors.toSet()),
            request.getParticipants().stream()
                .map(p -> Participant.valueOf(p.name()))
                .collect(Collectors.toSet()),
            request.getVideoDate(),
            userId);

    if (request.getLocationId() != null) {
      videoLocationService.addLocationInternal(video.getId(), request.getLocationId(), true);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(toVideoDetail(video));
  }

  @Override
  public ResponseEntity<VideoDetail> updateVideo(
      UUID id, UpdateVideoRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());

    Video video =
        videoService.updateVideo(
            id,
            request.getAmendments() != null
                ? request.getAmendments().stream()
                    .map(a -> Amendment.valueOf(a.name()))
                    .collect(Collectors.toSet())
                : null,
            request.getParticipants() != null
                ? request.getParticipants().stream()
                    .map(p -> Participant.valueOf(p.name()))
                    .collect(Collectors.toSet())
                : null,
            request.getVideoDate(),
            userId);

    return ResponseEntity.ok(toVideoDetail(video));
  }

  @Override
  public ResponseEntity<Void> deleteVideo(UUID id, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    videoService.deleteVideo(id, userId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<VideoLocationsResponse> getVideoLocations(UUID id) {
    List<VideoLocation> locations = videoLocationService.getVideoLocations(id);
    return ResponseEntity.ok(toVideoLocationsResponse(locations));
  }

  @Override
  public ResponseEntity<com.accountabilityatlas.videoservice.web.model.VideoLocation> addVideoLocation(
      UUID id, AddVideoLocationRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());

    VideoLocation location =
        videoLocationService.addLocation(
            id, request.getLocationId(), Boolean.TRUE.equals(request.getIsPrimary()), userId);

    return ResponseEntity.status(HttpStatus.CREATED).body(toVideoLocationModel(location));
  }

  @Override
  public ResponseEntity<Void> removeVideoLocation(
      UUID id, UUID locationId, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    videoLocationService.removeLocation(id, locationId, userId);
    return ResponseEntity.noContent().build();
  }

  private VideoDetail toVideoDetail(Video video) {
    VideoDetail detail = new VideoDetail();
    detail.setId(video.getId());
    detail.setYoutubeId(video.getYoutubeId());
    detail.setTitle(video.getTitle());
    detail.setDescription(video.getDescription());
    detail.setThumbnailUrl(video.getThumbnailUrl());
    detail.setDurationSeconds(video.getDurationSeconds());
    detail.setChannelId(video.getChannelId());
    detail.setChannelName(video.getChannelName());
    detail.setPublishedAt(
        video.getPublishedAt() != null
            ? video.getPublishedAt().atOffset(java.time.ZoneOffset.UTC)
            : null);
    detail.setVideoDate(video.getVideoDate());
    detail.setAmendments(
        video.getAmendments().stream()
            .map(a -> com.accountabilityatlas.videoservice.web.model.Amendment.valueOf(a.name()))
            .toList());
    detail.setParticipants(
        video.getParticipants().stream()
            .map(p -> com.accountabilityatlas.videoservice.web.model.Participant.valueOf(p.name()))
            .toList());
    detail.setStatus(
        com.accountabilityatlas.videoservice.web.model.VideoStatus.valueOf(video.getStatus().name()));
    detail.setSubmittedBy(video.getSubmittedBy());
    detail.setCreatedAt(video.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
    detail.setLocations(video.getLocations().stream().map(this::toVideoLocationModel).toList());
    return detail;
  }

  private VideoListResponse toVideoListResponse(Page<Video> page) {
    VideoListResponse response = new VideoListResponse();
    response.setContent(page.getContent().stream().map(this::toVideoSummary).toList());
    response.setPage(page.getNumber());
    response.setSize(page.getSize());
    response.setTotalElements((int) page.getTotalElements());
    response.setTotalPages(page.getTotalPages());
    return response;
  }

  private VideoSummary toVideoSummary(Video video) {
    VideoSummary summary = new VideoSummary();
    summary.setId(video.getId());
    summary.setYoutubeId(video.getYoutubeId());
    summary.setTitle(video.getTitle());
    summary.setThumbnailUrl(video.getThumbnailUrl());
    summary.setDurationSeconds(video.getDurationSeconds());
    summary.setAmendments(
        video.getAmendments().stream()
            .map(a -> com.accountabilityatlas.videoservice.web.model.Amendment.valueOf(a.name()))
            .toList());
    summary.setParticipants(
        video.getParticipants().stream()
            .map(p -> com.accountabilityatlas.videoservice.web.model.Participant.valueOf(p.name()))
            .toList());
    summary.setStatus(
        com.accountabilityatlas.videoservice.web.model.VideoStatus.valueOf(video.getStatus().name()));
    summary.setCreatedAt(video.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
    return summary;
  }

  private com.accountabilityatlas.videoservice.web.model.VideoLocation toVideoLocationModel(
      VideoLocation location) {
    var model = new com.accountabilityatlas.videoservice.web.model.VideoLocation();
    model.setId(location.getId());
    model.setVideoId(location.getVideo().getId());
    model.setLocationId(location.getLocationId());
    model.setIsPrimary(location.isPrimary());

    if (location.getDisplayName() != null) {
      LocationSummary locSummary = new LocationSummary();
      locSummary.setId(location.getLocationId());
      locSummary.setDisplayName(location.getDisplayName());
      locSummary.setCity(location.getCity());
      locSummary.setState(location.getState());
      if (location.getLatitude() != null && location.getLongitude() != null) {
        Coordinates coords = new Coordinates();
        coords.setLatitude(location.getLatitude());
        coords.setLongitude(location.getLongitude());
        locSummary.setCoordinates(coords);
      }
      model.setLocation(locSummary);
    }

    return model;
  }

  private VideoLocationsResponse toVideoLocationsResponse(List<VideoLocation> locations) {
    VideoLocationsResponse response = new VideoLocationsResponse();
    response.setLocations(locations.stream().map(this::toVideoLocationModel).toList());
    return response;
  }
}
