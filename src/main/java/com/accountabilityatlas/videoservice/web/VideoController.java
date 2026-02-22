package com.accountabilityatlas.videoservice.web;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.service.MetadataExtractionService;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import com.accountabilityatlas.videoservice.service.YouTubeService;
import com.accountabilityatlas.videoservice.service.YouTubeService.YouTubeMetadata;
import com.accountabilityatlas.videoservice.web.api.VideoLocationsApi;
import com.accountabilityatlas.videoservice.web.api.VideosApi;
import com.accountabilityatlas.videoservice.web.model.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
  private final YouTubeService youTubeService;
  private final MetadataExtractionService metadataExtractionService;

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
    VideoStatus domainStatus = getDomainStatus(status, isOwner);

    if (!isOwner) {
      domainStatus = VideoStatus.APPROVED;
    }

    Page<Video> videos = videoService.listVideosByUser(userId, domainStatus, pageable);
    return ResponseEntity.ok(toVideoListResponse(videos));
  }

  private static @Nullable VideoStatus getDomainStatus(
      com.accountabilityatlas.videoservice.web.model.VideoStatus status, boolean isOwner) {
    if (status != null) return VideoStatus.valueOf(status.name());
    else {
      return isOwner ? null : VideoStatus.APPROVED;
    }
  }

  /** Create a new video submission for the authenticated user with a required location. */
  @Override
  public ResponseEntity<VideoDetail> createVideo(CreateVideoRequest request) {
    UUID userId = requireCurrentUserId();
    UUID locationId = request.getLocationId();
    String trustTier = getCurrentTrustTierOrNull();

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
            List.of(locationId));

    videoLocationService.addLocationInternal(video.getId(), locationId, true);

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

  /** Preview YouTube video metadata without creating a video record. */
  @Override
  public ResponseEntity<VideoPreview> previewVideo(String youtubeUrl) {
    String videoId = youTubeService.extractVideoId(youtubeUrl);
    YouTubeMetadata metadata = youTubeService.fetchMetadata(videoId);

    VideoPreview preview = new VideoPreview();
    preview.setYoutubeId(metadata.videoId());
    preview.setTitle(metadata.title());
    preview.setDescription(metadata.description());
    preview.setThumbnailUrl(toUriOrNull(metadata.thumbnailUrl()));
    preview.setDurationSeconds(metadata.durationSeconds());
    preview.setChannelId(metadata.channelId());
    preview.setChannelName(metadata.channelName());
    preview.setPublishedAt(
        metadata.publishedAt() != null
            ? metadata.publishedAt().atOffset(java.time.ZoneOffset.UTC)
            : null);

    videoService
        .findByYoutubeId(videoId)
        .ifPresentOrElse(
            existing -> {
              preview.setAlreadyExists(true);
              preview.setExistingVideoId(existing.getId());
            },
            () -> {
              preview.setAlreadyExists(false);
              preview.setExistingVideoId(null);
            });

    return ResponseEntity.ok(preview);
  }

  /** Extract metadata from a YouTube video using AI. */
  @Override
  public ResponseEntity<VideoMetadataExtraction> extractVideoMetadata(String youtubeUrl) {
    requireCurrentUserId();
    String videoId = youTubeService.extractVideoId(youtubeUrl);
    YouTubeMetadata metadata = youTubeService.fetchMetadata(videoId);
    String publishedAt = metadata.publishedAt() != null ? metadata.publishedAt().toString() : null;
    MetadataExtractionService.ExtractionResult result =
        metadataExtractionService.extract(metadata.title(), metadata.description(), publishedAt);
    return ResponseEntity.ok(toVideoMetadataExtraction(result));
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
    detail.setRejectionReason(video.getRejectionReason());
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
    summary.setRejectionReason(video.getRejectionReason());
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
      LocationSummary locSummary = getLocationSummary(location);
      model.setLocation(locSummary);
    }

    return model;
  }

  private static @NonNull LocationSummary getLocationSummary(VideoLocation location) {
    LocationSummary locSummary = new LocationSummary();
    locSummary.setId(location.getLocationId());
    locSummary.setDisplayName(location.getDisplayName());
    locSummary.setCity(location.getCity());
    locSummary.setState(location.getState());
    if (location.getLatitude() != null && location.getLongitude() != null) {
      Coordinates coordinates = new Coordinates();
      coordinates.setLatitude(location.getLatitude());
      coordinates.setLongitude(location.getLongitude());
      locSummary.setCoordinates(coordinates);
    }
    return locSummary;
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

  /** Map an AI extraction result to the API model. */
  private VideoMetadataExtraction toVideoMetadataExtraction(
      MetadataExtractionService.ExtractionResult result) {
    VideoMetadataExtraction extraction = new VideoMetadataExtraction();
    extraction.setAmendments(
        result.amendments() != null
            ? result.amendments().stream()
                .filter(this::isValidAmendment)
                .map(com.accountabilityatlas.videoservice.web.model.Amendment::valueOf)
                .toList()
            : List.of());
    extraction.setParticipants(
        result.participants() != null
            ? result.participants().stream()
                .filter(this::isValidParticipant)
                .map(com.accountabilityatlas.videoservice.web.model.Participant::valueOf)
                .toList()
            : List.of());
    extraction.setVideoDate(
        result.videoDate() != null ? java.time.LocalDate.parse(result.videoDate()) : null);

    if (result.location() != null) {
      ExtractedLocation loc = new ExtractedLocation();
      loc.setName(result.location().name());
      loc.setCity(result.location().city());
      loc.setState(result.location().state());
      loc.setLatitude(result.location().latitude());
      loc.setLongitude(result.location().longitude());
      extraction.setLocation(loc);
    }

    if (result.confidence() != null) {
      ConfidenceScores scores = new ConfidenceScores();
      scores.setAmendments(result.confidence().amendments());
      scores.setParticipants(result.confidence().participants());
      scores.setVideoDate(result.confidence().videoDate());
      scores.setLocation(result.confidence().location());
      extraction.setConfidence(scores);
    }

    return extraction;
  }

  private boolean isValidAmendment(String value) {
    try {
      com.accountabilityatlas.videoservice.web.model.Amendment.valueOf(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean isValidParticipant(String value) {
    try {
      com.accountabilityatlas.videoservice.web.model.Participant.valueOf(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
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
