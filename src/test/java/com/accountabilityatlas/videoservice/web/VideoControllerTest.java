package com.accountabilityatlas.videoservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import com.accountabilityatlas.videoservice.web.model.AddVideoLocationRequest;
import com.accountabilityatlas.videoservice.web.model.CreateVideoRequest;
import com.accountabilityatlas.videoservice.web.model.UpdateVideoRequest;
import com.accountabilityatlas.videoservice.web.model.VideoDetail;
import com.accountabilityatlas.videoservice.web.model.VideoLocationsResponse;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
  void getVideo_mapsDetail() {
    Video video = sampleVideo();
    when(videoService.getVideo(video.getId())).thenReturn(video);

    VideoDetail detail = videoController.getVideo(video.getId()).getBody();

    assertThat(detail.getId()).isEqualTo(video.getId());
    assertThat(detail.getYoutubeId()).isEqualTo(video.getYoutubeId());
    assertThat(detail.getThumbnailUrl()).isEqualTo(URI.create(video.getThumbnailUrl()));
  }

  @Test
  void listVideos_usesApprovedForOtherUsers() {
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    UUID submittedBy = UUID.randomUUID();
    when(videoService.listVideosByUser(
            eq(submittedBy), eq(VideoStatus.APPROVED), any(PageRequest.class)))
        .thenReturn(page);

    var response =
        videoController.listVideos(null, null, null, submittedBy, 0, 20, "createdAt", "desc");

    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void getVideosByUser_ownerCanSeeAllStatuses() {
    setAuth();
    Video video = sampleVideo();
    Page<Video> page = new PageImpl<>(List.of(video), PageRequest.of(0, 20), 1);
    when(videoService.listVideosByUser(eq(userId), isNull(), any(PageRequest.class)))
        .thenReturn(page);

    var response = videoController.getVideosByUser(userId, null, 0, 20);

    assertThat(response.getBody().getContent()).hasSize(1);
  }

  @Test
  void createVideo_requiresAuth() {
    CreateVideoRequest request = new CreateVideoRequest();
    request.setYoutubeUrl(URI.create("https://youtu.be/dQw4w9WgXcQ"));
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FIRST));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.POLICE));

    assertThatThrownBy(() -> videoController.createVideo(request))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void createVideo_createsAndMaps() {
    setAuth();
    CreateVideoRequest request = new CreateVideoRequest();
    request.setYoutubeUrl(URI.create("https://youtu.be/dQw4w9WgXcQ"));
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FIRST));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.POLICE));
    request.setVideoDate(LocalDate.of(2024, 1, 2));

    Video video = sampleVideo();
    when(videoService.createVideo(any(), anySet(), anySet(), any(), any())).thenReturn(video);

    VideoDetail detail = videoController.createVideo(request).getBody();

    assertThat(detail.getId()).isEqualTo(video.getId());
    verify(videoService)
        .createVideo(eq(request.getYoutubeUrl().toString()), anySet(), anySet(), any(), eq(userId));
  }

  @Test
  void updateVideo_updates() {
    setAuth();
    UpdateVideoRequest request = new UpdateVideoRequest();
    request.setAmendments(List.of(com.accountabilityatlas.videoservice.web.model.Amendment.FOURTH));
    request.setParticipants(
        List.of(com.accountabilityatlas.videoservice.web.model.Participant.CITIZEN));

    Video video = sampleVideo();
    when(videoService.updateVideo(any(), any(), any(), any(), any())).thenReturn(video);

    VideoDetail detail = videoController.updateVideo(video.getId(), request).getBody();

    assertThat(detail.getId()).isEqualTo(video.getId());
  }

  @Test
  void deleteVideo_removes() {
    setAuth();
    Video video = sampleVideo();

    videoController.deleteVideo(video.getId());

    verify(videoService).deleteVideo(video.getId(), userId);
  }

  @Test
  void getVideoLocations_mapsResponse() {
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

    VideoLocationsResponse response = videoController.getVideoLocations(video.getId()).getBody();

    assertThat(response.getLocations()).hasSize(1);
    assertThat(response.getLocations().get(0).getLocation().getDisplayName()).isEqualTo("Display");
  }

  @Test
  void addVideoLocation_requiresAuth() {
    AddVideoLocationRequest request = new AddVideoLocationRequest();
    request.setLocationId(UUID.randomUUID());

    assertThatThrownBy(() -> videoController.addVideoLocation(UUID.randomUUID(), request))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void addVideoLocation_creates() {
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

    var response = videoController.addVideoLocation(video.getId(), request);

    assertThat(response.getBody().getLocationId()).isEqualTo(request.getLocationId());
  }

  @Test
  void removeVideoLocation_requiresAuth() {
    assertThatThrownBy(
            () -> videoController.removeVideoLocation(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }

  private void setAuth() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("sub", userId.toString())
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
