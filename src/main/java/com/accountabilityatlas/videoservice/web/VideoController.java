package com.accountabilityatlas.videoservice.web;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import com.accountabilityatlas.videoservice.web.api.VideoLocationsApi;
import com.accountabilityatlas.videoservice.web.api.VideosApi;
import com.accountabilityatlas.videoservice.web.model.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VideoController implements VideosApi, VideoLocationsApi {

  private final VideoService videoService;
  private final VideoLocationService videoLocationService;

  /**
   * Fetch a single video by id, enforcing visibility rules based on the caller's identity and trust
   * tier.
   */
  @Override
  public ResponseEntity<VideoDetail> getVideo(UUID id) {
    Video video = videoService.getVideo(id);
    UUID currentUserId = getCurrentUserIdOrNull();
    String trustTier = getCurrentTrustTierOrNull();
    if (!videoService.canView(video, currentUserId, trustTier)) {
      throw new UnauthorizedException("You do not have permission to view this video");
    }
    return ResponseEntity.ok(toVideoDetail(video));
  }

  /**
   * List videos with paging and sorting, clamping visibility for anonymous or non-moderator callers
   * to approved content.
   */
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
    UUID currentUserId = getCurrentUserIdOrNull();
    String trustTier = getCurrentTrustTierOrNull();
    boolean isModeratorOrAdmin = "MODERATOR".equals(trustTier) || "ADMIN".equals(trustTier);

    if (submittedBy != null) {
      if (!submittedBy.equals(currentUserId) && !isModeratorOrAdmin) {
        domainStatus = VideoStatus.APPROVED;
      }
    } else if (!isModeratorOrAdmin) {
      domainStatus = VideoStatus.APPROVED;
    }

    Page<Video> videos =
        submittedBy != null
            ? videoService.listVideosByUser(submittedBy, domainStatus, pageable)
            : videoService.listVideos(domainStatus, pageable);

    return ResponseEntity.ok(toVideoListResponse(videos));
  }

  /** List videos submitted by a specific user. Non-owners are restricted to approved content. */
  @Override
  public ResponseEntity<VideoListResponse> getVideosByUser(
      UUID userId,
      com.accountabilityatlas.videoservice.web.model.VideoStatus status,
      Integer page,
      Integer size) {
    PageRequest pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

    UUID currentUserId = getCurrentUserIdOrNull();
    boolean isOwner = currentUserId != null && currentUserId.equals(userId);
    VideoStatus domainStatus =
        status != null
            ? VideoStatus.valueOf(status.name())
            : (isOwner ? null : VideoStatus.APPROVED);

    if (!isOwner) {
      domainStatus = VideoStatus.APPROVED;
    }

    Page<Video> videos = videoService.listVideosByUser(userId, domainStatus, pageable);
    return ResponseEntity.ok(toVideoListResponse(videos));
  }

  /** Create a new video submission for the authenticated user and optionally attach a location. */
  @Override
  public ResponseEntity<VideoDetail> createVideo(CreateVideoRequest request) {
    UUID userId = requireCurrentUserId();
    String trustTier = getCurrentTrustTierOrNull();
    List<UUID> locationIds =
        request.getLocationId() != null ? List.of(request.getLocationId()) : List.of();

    Video video =
        videoService.createVideo(
            request.getYoutubeUrl().toString(),
            request.getAmendments().stream()
                .map(a -> Amendment.valueOf(a.name()))
                .collect(Collectors.toSet()),
            request.getParticipants().stream()
                .map(p -> Participant.valueOf(p.name()))
                .collect(Collectors.toSet()),
            request.getVideoDate(),
            userId,
            trustTier != null ? trustTier : "NEW",
            locationIds);

    if (request.getLocationId() != null) {
      videoLocationService.addLocationInternal(video.getId(), request.getLocationId(), true);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(toVideoDetail(video));
  }

  /** Update an existing video owned by the authenticated user. */
  @Override
  public ResponseEntity<VideoDetail> updateVideo(UUID id, UpdateVideoRequest request) {
    UUID userId = requireCurrentUserId();

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

  /** Delete a video owned by the authenticated user. */
  @Override
  public ResponseEntity<Void> deleteVideo(UUID id) {
    UUID userId = requireCurrentUserId();
    videoService.deleteVideo(id, userId);
    return ResponseEntity.noContent().build();
  }

  /** Fetch locations linked to a video by id. */
  @Override
  public ResponseEntity<VideoLocationsResponse> getVideoLocations(UUID id) {
    List<VideoLocation> locations = videoLocationService.getVideoLocations(id);
    return ResponseEntity.ok(toVideoLocationsResponse(locations));
  }

  /** Add a location link to a video owned by the authenticated user. */
  @Override
  public ResponseEntity<com.accountabilityatlas.videoservice.web.model.VideoLocation>
      addVideoLocation(UUID id, AddVideoLocationRequest request) {
    UUID userId = requireCurrentUserId();

    VideoLocation location =
        videoLocationService.addLocation(
            id, request.getLocationId(), Boolean.TRUE.equals(request.getIsPrimary()), userId);

    return ResponseEntity.status(HttpStatus.CREATED).body(toVideoLocationModel(location));
  }

  /** Remove a location link from a video owned by the authenticated user. */
  @Override
  public ResponseEntity<Void> removeVideoLocation(UUID id, UUID locationId) {
    UUID userId = requireCurrentUserId();
    videoLocationService.removeLocation(id, locationId, userId);
    return ResponseEntity.noContent().build();
  }

  /** Map a domain video to the detailed API model. */
  private VideoDetail toVideoDetail(Video video) {
    VideoDetail detail = new VideoDetail();
    detail.setId(video.getId());
    detail.setYoutubeId(video.getYoutubeId());
    detail.setTitle(video.getTitle());
    detail.setDescription(video.getDescription());
    detail.setThumbnailUrl(toUriOrNull(video.getThumbnailUrl()));
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
        com.accountabilityatlas.videoservice.web.model.VideoStatus.valueOf(
            video.getStatus().name()));
    detail.setSubmittedBy(video.getSubmittedBy());
    detail.setCreatedAt(video.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
    detail.setLocations(video.getLocations().stream().map(this::toVideoLocationModel).toList());
    return detail;
  }

  /** Convert a page of domain videos to a list response with paging metadata. */
  private VideoListResponse toVideoListResponse(Page<Video> page) {
    VideoListResponse response = new VideoListResponse();
    response.setContent(page.getContent().stream().map(this::toVideoSummary).toList());
    response.setPage(page.getNumber());
    response.setSize(page.getSize());
    response.setTotalElements((int) page.getTotalElements());
    response.setTotalPages(page.getTotalPages());
    return response;
  }

  /** Map a domain video to the summary API model used in lists. */
  private VideoSummary toVideoSummary(Video video) {
    VideoSummary summary = new VideoSummary();
    summary.setId(video.getId());
    summary.setYoutubeId(video.getYoutubeId());
    summary.setTitle(video.getTitle());
    summary.setThumbnailUrl(toUriOrNull(video.getThumbnailUrl()));
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
        com.accountabilityatlas.videoservice.web.model.VideoStatus.valueOf(
            video.getStatus().name()));
    summary.setCreatedAt(video.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
    return summary;
  }

  /** Map a domain location link to the API model, including a summary if present. */
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

  /** Convert a list of domain location links to the list response model. */
  private VideoLocationsResponse toVideoLocationsResponse(List<VideoLocation> locations) {
    VideoLocationsResponse response = new VideoLocationsResponse();
    response.setLocations(locations.stream().map(this::toVideoLocationModel).toList());
    return response;
  }

  /** Return the current user's id or throw if unauthenticated. */
  private UUID requireCurrentUserId() {
    UUID userId = getCurrentUserIdOrNull();
    if (userId == null) {
      throw new UnauthorizedException("Missing or invalid authentication token");
    }
    return userId;
  }

  /** Return the current user's id from the JWT, or null when unauthenticated. */
  private UUID getCurrentUserIdOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }
    return UUID.fromString(jwt.getSubject());
  }

  /** Return the current user's trust tier from the JWT, or null when unauthenticated. */
  private String getCurrentTrustTierOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }
    return jwt.getClaimAsString("trustTier");
  }

  /** Safely parse a string into a URI, returning null for blank or invalid values. */
  private URI toUriOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return URI.create(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
