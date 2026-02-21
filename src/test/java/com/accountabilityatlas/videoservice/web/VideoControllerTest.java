package com.accountabilityatlas.videoservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.InvalidYouTubeUrlException;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.service.MetadataExtractionService;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import com.accountabilityatlas.videoservice.service.YouTubeService;
import com.accountabilityatlas.videoservice.service.YouTubeService.YouTubeMetadata;
import com.accountabilityatlas.videoservice.web.model.*;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

  @Mock private VideoService videoService;
  @Mock private VideoLocationService videoLocationService;
  @Mock private YouTubeService youTubeService;
  @Mock private MetadataExtractionService metadataExtractionService;

  @InjectMocks private VideoController videoController;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getVideo_visibleVideo_mapsDetail() {
    // Arrange
    Video video = sampleVideo();
    when(videoService.getVideo(video.getId())).thenReturn(video);
    when(videoService.canView(eq(video), any(), any())).thenReturn(true);

    // Act
    VideoDetail detail = videoController.getVideo(video.getId()).getBody();

    // Assert
    assertNotNull(detail);
    assertThat(detail.getId()).isEqualTo(video.getId());
    assertThat(detail.getYoutubeId()).isEqualTo(video.getYoutubeId());
    assertThat(detail.getThumbnailUrl()).isEqualTo(URI.create(video.getThumbnailUrl()));
  }

  @Test
  void getVideo_notAllowed_throwsUnauthorized() {
    // Arrange
    Video video = sampleVideo();
    when(videoService.getVideo(video.getId())).thenReturn(video);
    when(videoService.canView(eq(video), any(), any())).thenReturn(false);

    // Act
    Throwable thrown = catchThrowable(() -> videoController.getVideo(video.getId()));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void getVideo_trustTierInJwt_mapsDetail() {
    // Arrange
    setAuthWithTrustTier("ADMIN");
    Video video = sampleVideo();
    when(videoService.getVideo(video.getId())).thenReturn(video);
    when(videoService.canView(video, userId, "ADMIN")).thenReturn(true);

    // Act
    VideoDetail detail = videoController.getVideo(video.getId()).getBody();

    // Assert
    assertNotNull(detail);
    assertThat(detail.getId()).isEqualTo(video.getId());
  }

  @Test
  void listVideos_submittedByOtherUser_clampsToApproved() {
    // Arrange
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    UUID submittedBy = UUID.randomUUID();
    when(videoService.listVideosByUser(
            eq(submittedBy), eq(VideoStatus.APPROVED), any(PageRequest.class)))
        .thenReturn(page);

    // Act
    var response =
        videoController.listVideos(null, null, null, submittedBy, 0, 20, "createdAt", "desc");

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void listVideos_anonymousRequest_clampsToApproved() {
    // Arrange
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideos(eq(VideoStatus.APPROVED), any(PageRequest.class)))
        .thenReturn(page);

    // Act
    var response = videoController.listVideos(null, null, null, null, 0, 20, null, null);

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void listVideos_nonModeratorRequestedStatus_clampsToApproved() {
    // Arrange
    setAuthWithTrustTier("TRUSTED");
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideos(eq(VideoStatus.APPROVED), any(PageRequest.class)))
        .thenReturn(page);

    // Act
    var response =
        videoController.listVideos(
            com.accountabilityatlas.videoservice.web.model.VideoStatus.PENDING,
            null,
            null,
            null,
            0,
            20,
            "createdAt",
            "desc");

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void listVideos_moderatorRequestedStatus_usesRequestedStatus() {
    // Arrange
    setAuthWithTrustTier("MODERATOR");
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideos(eq(VideoStatus.PENDING), any(PageRequest.class))).thenReturn(page);

    // Act
    var response =
        videoController.listVideos(
            com.accountabilityatlas.videoservice.web.model.VideoStatus.PENDING,
            null,
            null,
            null,
            0,
            20,
            "createdAt",
            "desc");

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void getVideosByUser_ownerRequest_returnsAllStatuses() {
    // Arrange
    setAuth();
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideosByUser(eq(userId), isNull(), any(PageRequest.class)))
        .thenReturn(page);

    // Act
    var response = videoController.getVideosByUser(userId, null, 0, 20);

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void getVideosByUser_nonOwnerRequestedStatus_clampsToApproved() {
    // Arrange
    UUID targetUserId = UUID.randomUUID();
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideosByUser(
            eq(targetUserId), eq(VideoStatus.APPROVED), any(PageRequest.class)))
        .thenReturn(page);

    // Act
    var response =
        videoController.getVideosByUser(
            targetUserId,
            com.accountabilityatlas.videoservice.web.model.VideoStatus.PENDING,
            0,
            20);

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void createVideo_unauthenticated_throwsUnauthorized() {
    // Arrange
    CreateVideoRequest request = new CreateVideoRequest();
    request.setYoutubeUrl(URI.create("https://youtu.be/dQw4w9WgXcQ"));
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FIRST));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.POLICE));
    request.setLocationId(UUID.randomUUID());

    // Act
    Throwable thrown = catchThrowable(() -> videoController.createVideo(request));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void createVideo_authenticated_mapsDetail() {
    // Arrange
    setAuth();
    UUID locationId = UUID.randomUUID();
    CreateVideoRequest request = new CreateVideoRequest();
    request.setYoutubeUrl(URI.create("https://youtu.be/dQw4w9WgXcQ"));
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FIRST));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.POLICE));
    request.setVideoDate(LocalDate.of(2024, 1, 2));
    request.setLocationId(locationId);

    Video video = sampleVideo();
    when(videoService.createVideo(any(), anySet(), anySet(), any(), any(), any(), anyList()))
        .thenReturn(video);

    // Act
    VideoDetail detail = videoController.createVideo(request).getBody();

    // Assert
    assertNotNull(detail);
    assertThat(detail.getId()).isEqualTo(video.getId());
    verify(videoService)
        .createVideo(
            eq(request.getYoutubeUrl().toString()),
            anySet(),
            anySet(),
            any(),
            eq(userId),
            any(),
            anyList());
    verify(videoLocationService).addLocationInternal(video.getId(), locationId, true);
  }

  @Test
  void updateVideo_authenticated_mapsDetail() {
    // Arrange
    setAuth();
    UpdateVideoRequest request = new UpdateVideoRequest();
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FOURTH));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.CITIZEN));

    Video video = sampleVideo();
    when(videoService.updateVideo(any(), any(), any(), any(), any())).thenReturn(video);

    // Act
    VideoDetail detail = videoController.updateVideo(video.getId(), request).getBody();

    // Assert
    assertNotNull(detail);
    assertThat(detail.getId()).isEqualTo(video.getId());
  }

  @Test
  void deleteVideo_authenticated_deletesVideo() {
    // Arrange
    setAuth();
    Video video = sampleVideo();

    // Act
    videoController.deleteVideo(video.getId());

    // Assert
    verify(videoService).deleteVideo(video.getId(), userId);
  }

  @Test
  void getVideoLocations_existingVideo_mapsResponse() {
    // Arrange
    VideoLocation location = new VideoLocation();
    location.setId(UUID.randomUUID());
    location.setLocationId(UUID.randomUUID());

    Video video = sampleVideo();
    location.setVideo(video);
    location.setPrimary(true);
    location.setDisplayName("Display");
    location.setCity("City");
    location.setState("State");
    location.setLatitude(1.0);
    location.setLongitude(2.0);

    when(videoLocationService.getVideoLocations(video.getId())).thenReturn(List.of(location));

    // Act
    VideoLocationsResponse response = videoController.getVideoLocations(video.getId()).getBody();

    // Assert
    assertNotNull(response);
    assertThat(response.getLocations()).hasSize(1);
    assertNotNull(response.getLocations().getFirst().getLocation());
    assertThat(response.getLocations().getFirst().getLocation().getDisplayName())
        .isEqualTo("Display");
  }

  @Test
  void addVideoLocation_unauthenticated_throwsUnauthorized() {
    // Arrange
    AddVideoLocationRequest request = new AddVideoLocationRequest();
    request.setLocationId(UUID.randomUUID());

    // Act
    Throwable thrown =
        catchThrowable(() -> videoController.addVideoLocation(UUID.randomUUID(), request));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void addVideoLocation_authenticated_createsLocation() {
    // Arrange
    setAuth();
    AddVideoLocationRequest request = new AddVideoLocationRequest();
    request.setLocationId(UUID.randomUUID());
    request.setIsPrimary(true);

    VideoLocation location = new VideoLocation();
    location.setId(UUID.randomUUID());
    Video video = sampleVideo();
    location.setVideo(video);
    location.setLocationId(request.getLocationId());

    when(videoLocationService.addLocation(any(), any(), anyBoolean(), any())).thenReturn(location);

    // Act
    var response = videoController.addVideoLocation(video.getId(), request);

    // Assert
    assertNotNull(response.getBody());
    assertThat(response.getBody().getLocationId()).isEqualTo(request.getLocationId());
  }

  @Test
  void removeVideoLocation_unauthenticated_throwsUnauthorized() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();

    // Act
    Throwable thrown =
        catchThrowable(() -> videoController.removeVideoLocation(videoId, locationId));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void previewVideo_validUrl_returnsMetadata() {
    // Arrange
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    String videoId = "dQw4w9WgXcQ";
    YouTubeMetadata metadata =
        new YouTubeMetadata(
            videoId,
            "Test Title",
            "Test Description",
            "http://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            212,
            "UCuAXFkgsw1L7xaCfnd5JJOw",
            "Test Channel",
            Instant.parse("2024-06-01T12:00:00Z"),
            null);
    when(youTubeService.extractVideoId(url)).thenReturn(videoId);
    when(youTubeService.fetchMetadata(videoId)).thenReturn(metadata);
    when(videoService.findByYoutubeId(videoId)).thenReturn(Optional.empty());

    // Act
    VideoPreview preview = videoController.previewVideo(url).getBody();

    // Assert
    assertNotNull(preview);
    assertThat(preview.getYoutubeId()).isEqualTo(videoId);
    assertThat(preview.getTitle()).isEqualTo("Test Title");
    assertThat(preview.getDescription()).isEqualTo("Test Description");
    assertThat(preview.getDurationSeconds()).isEqualTo(212);
    assertThat(preview.getChannelId()).isEqualTo("UCuAXFkgsw1L7xaCfnd5JJOw");
    assertThat(preview.getChannelName()).isEqualTo("Test Channel");
    assertThat(preview.getAlreadyExists()).isFalse();
    assertThat(preview.getExistingVideoId()).isNull();
  }

  @Test
  void previewVideo_alreadyExists_returnsExistingVideoId() {
    // Arrange
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    String videoId = "dQw4w9WgXcQ";
    YouTubeMetadata metadata =
        new YouTubeMetadata(
            videoId,
            "Test Title",
            null,
            "http://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            212,
            "UCuAXFkgsw1L7xaCfnd5JJOw",
            "Test Channel",
            Instant.parse("2024-06-01T12:00:00Z"),
            null);
    Video existingVideo = sampleVideo();
    when(youTubeService.extractVideoId(url)).thenReturn(videoId);
    when(youTubeService.fetchMetadata(videoId)).thenReturn(metadata);
    when(videoService.findByYoutubeId(videoId)).thenReturn(Optional.of(existingVideo));

    // Act
    VideoPreview preview = videoController.previewVideo(url).getBody();

    // Assert
    assertNotNull(preview);
    assertThat(preview.getAlreadyExists()).isTrue();
    assertThat(preview.getExistingVideoId()).isEqualTo(existingVideo.getId());
  }

  @Test
  void previewVideo_invalidUrl_throwsInvalidYouTubeUrl() {
    // Arrange
    String url = "https://example.com/not-a-video";
    when(youTubeService.extractVideoId(url)).thenThrow(new InvalidYouTubeUrlException(url));

    // Act
    Throwable thrown = catchThrowable(() -> videoController.previewVideo(url));

    // Assert
    assertThat(thrown).isInstanceOf(InvalidYouTubeUrlException.class);
  }

  @Test
  void extractVideoMetadata_unauthenticated_throwsUnauthorized() {
    Throwable thrown =
        catchThrowable(
            () ->
                videoController.extractVideoMetadata(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void extractVideoMetadata_authenticated_returnsExtraction() {
    setAuth();
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    String videoId = "dQw4w9WgXcQ";
    YouTubeMetadata metadata =
        new YouTubeMetadata(
            videoId,
            "First Amendment Audit - City Hall",
            "Auditing city hall",
            "http://thumb",
            300,
            "channel",
            "Channel",
            Instant.parse("2024-01-01T00:00:00Z"),
            "2024-01-01");
    MetadataExtractionService.ExtractionResult result =
        new MetadataExtractionService.ExtractionResult(
            List.of("FIRST"),
            List.of("POLICE", "CITIZEN"),
            "2024-03-15",
            new MetadataExtractionService.LocationSuggestion(
                "City Hall", "Springfield", "IL", null, null),
            new MetadataExtractionService.ConfidenceScores(0.9, 0.85, 0.6, 0.8));

    when(youTubeService.extractVideoId(url)).thenReturn(videoId);
    when(youTubeService.fetchMetadata(videoId)).thenReturn(metadata);
    when(metadataExtractionService.extract(
            metadata.title(), metadata.description(), metadata.recordingDate()))
        .thenReturn(result);

    VideoMetadataExtraction extraction = videoController.extractVideoMetadata(url).getBody();

    assertNotNull(extraction);
    assertThat(extraction.getAmendments())
        .containsExactly(com.accountabilityatlas.videoservice.web.model.Amendment.FIRST);
    assertThat(extraction.getParticipants()).hasSize(2);
    assertThat(extraction.getVideoDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    assertNotNull(extraction.getLocation());
    assertThat(extraction.getLocation().getName()).isEqualTo("City Hall");
    assertNotNull(extraction.getConfidence());
  }

  @Test
  void extractVideoMetadata_invalidUrl_throwsInvalidYouTubeUrl() {
    setAuth();
    String url = "https://example.com/not-a-video";
    when(youTubeService.extractVideoId(url)).thenThrow(new InvalidYouTubeUrlException(url));

    Throwable thrown = catchThrowable(() -> videoController.extractVideoMetadata(url));

    assertThat(thrown).isInstanceOf(InvalidYouTubeUrlException.class);
  }

  @Test
  void extractVideoMetadata_serviceThrows_propagatesMetadataExtractionException() {
    setAuth();
    String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    String videoId = "dQw4w9WgXcQ";
    YouTubeMetadata metadata =
        new YouTubeMetadata(
            videoId,
            "Test Title",
            "Test Description",
            "http://thumb",
            300,
            "channel",
            "Channel",
            Instant.parse("2024-01-01T00:00:00Z"),
            null);

    when(youTubeService.extractVideoId(url)).thenReturn(videoId);
    when(youTubeService.fetchMetadata(videoId)).thenReturn(metadata);
    when(metadataExtractionService.extract(
            metadata.title(), metadata.description(), metadata.recordingDate()))
        .thenThrow(
            new com.accountabilityatlas.videoservice.exception.MetadataExtractionException(
                "AI extraction not configured"));

    Throwable thrown = catchThrowable(() -> videoController.extractVideoMetadata(url));

    assertThat(thrown)
        .isInstanceOf(
            com.accountabilityatlas.videoservice.exception.MetadataExtractionException.class)
        .hasMessageContaining("AI extraction not configured");
  }

  private void setAuth() {
    setAuthWithTrustTier(null);
  }

  private void setAuthWithTrustTier(String trustTier) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("sub", userId.toString())
            .claim("trustTier", trustTier)
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private Video sampleVideo() {
    Video video = new Video();
    video.setId(UUID.randomUUID());
    video.setYoutubeId("abc123def45");
    video.setTitle("Title");
    video.setDescription("Desc");
    video.setThumbnailUrl("http://thumb");
    video.setDurationSeconds(120);
    video.setChannelId("channel");
    video.setChannelName("Channel Name");
    video.setPublishedAt(Instant.parse("2024-01-01T00:00:00Z"));
    video.setVideoDate(LocalDate.of(2024, 1, 2));
    video.setAmendments(Set.of(Amendment.FIRST));
    video.setParticipants(Set.of(Participant.POLICE));
    video.setStatus(VideoStatus.PENDING);
    video.setSubmittedBy(userId);
    return video;
  }
}
