package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.exception.VideoAlreadyExistsException;
import com.accountabilityatlas.videoservice.exception.VideoNotFoundException;
import com.accountabilityatlas.videoservice.repository.VideoRepository;
import com.accountabilityatlas.videoservice.service.YouTubeService.YouTubeMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

  @Mock private VideoRepository videoRepository;
  @Mock private YouTubeService youTubeService;

  @InjectMocks private VideoService videoService;

  @Captor private ArgumentCaptor<Video> videoCaptor;

  private UUID videoId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    videoId = UUID.randomUUID();
    userId = UUID.randomUUID();
  }

  @Test
  void getVideo_missing_throws() {
    when(videoRepository.findById(videoId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> videoService.getVideo(videoId))
        .isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void listVideos_defaultsToApproved() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findByStatusOrderByCreatedAtDesc(VideoStatus.APPROVED, pageable))
        .thenReturn(Page.empty());

    Page<Video> result = videoService.listVideos(null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(0);
    verify(videoRepository).findByStatusOrderByCreatedAtDesc(VideoStatus.APPROVED, pageable);
  }

  @Test
  void listVideosByUser_withStatus() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findBySubmittedByAndStatus(userId, VideoStatus.PENDING, pageable))
        .thenReturn(Page.empty());

    videoService.listVideosByUser(userId, VideoStatus.PENDING, pageable);

    verify(videoRepository).findBySubmittedByAndStatus(userId, VideoStatus.PENDING, pageable);
  }

  @Test
  void listVideosByUser_withoutStatus() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findBySubmittedBy(userId, pageable)).thenReturn(Page.empty());

    videoService.listVideosByUser(userId, null, pageable);

    verify(videoRepository).findBySubmittedBy(userId, pageable);
  }

  @Test
  void createVideo_duplicateYoutubeId_throws() {
    when(youTubeService.extractVideoId("url")).thenReturn("abc123def45");
    when(videoRepository.existsByYoutubeId("abc123def45")).thenReturn(true);

    assertThatThrownBy(
            () ->
                videoService.createVideo(
                    "url", Set.of(Amendment.FIRST), Set.of(Participant.POLICE), null, userId))
        .isInstanceOf(VideoAlreadyExistsException.class);
  }

  @Test
  void createVideo_savesMetadata() {
    when(youTubeService.extractVideoId("url")).thenReturn("abc123def45");
    when(videoRepository.existsByYoutubeId("abc123def45")).thenReturn(false);

    YouTubeMetadata metadata =
        new YouTubeMetadata(
            "abc123def45",
            "Title",
            "Desc",
            "http://thumb",
            120,
            "channel",
            "Channel Name",
            Instant.parse("2024-01-01T00:00:00Z"));
    when(youTubeService.fetchMetadata("abc123def45")).thenReturn(metadata);
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Video created =
        videoService.createVideo(
            "url",
            Set.of(Amendment.FIRST),
            Set.of(Participant.POLICE),
            LocalDate.of(2024, 1, 2),
            userId);

    assertThat(created.getYoutubeId()).isEqualTo("abc123def45");
    assertThat(created.getTitle()).isEqualTo("Title");
    assertThat(created.getStatus()).isEqualTo(VideoStatus.PENDING);
    assertThat(created.getSubmittedBy()).isEqualTo(userId);
  }

  @Test
  void updateVideo_notOwner_throws() {
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(UUID.randomUUID());
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    assertThatThrownBy(
            () ->
                videoService.updateVideo(
                    videoId,
                    Set.of(Amendment.FOURTH),
                    Set.of(Participant.CITIZEN),
                    LocalDate.now(java.time.ZoneId.of("UTC")),
                    userId))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void updateVideo_updatesFields() {
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Video updated =
        videoService.updateVideo(
            videoId,
            Set.of(Amendment.SECOND),
            Set.of(Participant.GOVERNMENT),
            LocalDate.of(2024, 2, 2),
            userId);

    assertThat(updated.getAmendments()).containsExactly(Amendment.SECOND);
    assertThat(updated.getParticipants()).containsExactly(Participant.GOVERNMENT);
    assertThat(updated.getVideoDate()).isEqualTo(LocalDate.of(2024, 2, 2));
  }

  @Test
  void deleteVideo_setsDeleted() {
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    videoService.deleteVideo(videoId, userId);

    verify(videoRepository).save(videoCaptor.capture());
    assertThat(videoCaptor.getValue().getStatus()).isEqualTo(VideoStatus.DELETED);
  }

  @Test
  void updateVideoInternal_updatesFields() {
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Video updated =
        videoService.updateVideoInternal(
            videoId,
            Set.of(Amendment.FIRST),
            Set.of(Participant.BUSINESS),
            LocalDate.of(2023, 3, 3));

    assertThat(updated.getAmendments()).containsExactly(Amendment.FIRST);
    assertThat(updated.getParticipants()).containsExactly(Participant.BUSINESS);
    assertThat(updated.getVideoDate()).isEqualTo(LocalDate.of(2023, 3, 3));
  }

  @Test
  void updateVideoStatus_updates() {
    Video video = new Video();
    video.setId(videoId);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Video updated = videoService.updateVideoStatus(videoId, VideoStatus.APPROVED);

    assertThat(updated.getStatus()).isEqualTo(VideoStatus.APPROVED);
  }

  @Test
  void canView_rules() {
    Video approved = new Video();
    approved.setStatus(VideoStatus.APPROVED);

    Video pending = new Video();
    pending.setStatus(VideoStatus.PENDING);
    pending.setSubmittedBy(userId);

    Video deleted = new Video();
    deleted.setStatus(VideoStatus.DELETED);

    assertThat(videoService.canView(approved, null, null)).isTrue();
    assertThat(videoService.canView(pending, userId, null)).isTrue();
    assertThat(videoService.canView(pending, null, "MODERATOR")).isTrue();
    assertThat(videoService.canView(pending, null, "USER")).isFalse();
    assertThat(videoService.canView(deleted, null, "ADMIN")).isTrue();
    assertThat(videoService.canView(deleted, null, "MODERATOR")).isFalse();
  }
}
