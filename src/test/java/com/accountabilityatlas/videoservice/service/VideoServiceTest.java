package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Participant;
import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.event.VideoEventPublisher;
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
  @Mock private VideoEventPublisher videoEventPublisher;

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
  void getVideo_missingId_throwsNotFound() {
    // Arrange
    when(videoRepository.findById(videoId)).thenReturn(Optional.empty());

    // Act
    Throwable thrown = catchThrowable(() -> videoService.getVideo(videoId));

    // Assert
    assertThat(thrown).isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void listVideos_statusNull_defaultsToApproved() {
    // Arrange
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findByStatusOrderByCreatedAtDesc(VideoStatus.APPROVED, pageable))
        .thenReturn(Page.empty());

    // Act
    Page<Video> result = videoService.listVideos(null, pageable);

    // Assert
    assertThat(result.getTotalElements()).isEqualTo(0);
    verify(videoRepository).findByStatusOrderByCreatedAtDesc(VideoStatus.APPROVED, pageable);
  }

  @Test
  void listVideosByUser_statusProvided_usesStatusFilter() {
    // Arrange
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findBySubmittedByAndStatus(userId, VideoStatus.PENDING, pageable))
        .thenReturn(Page.empty());

    // Act
    videoService.listVideosByUser(userId, VideoStatus.PENDING, pageable);

    // Assert
    verify(videoRepository).findBySubmittedByAndStatus(userId, VideoStatus.PENDING, pageable);
  }

  @Test
  void listVideosByUser_statusMissing_listsAll() {
    // Arrange
    PageRequest pageable = PageRequest.of(0, 20);
    when(videoRepository.findBySubmittedBy(userId, pageable)).thenReturn(Page.empty());

    // Act
    videoService.listVideosByUser(userId, null, pageable);

    // Assert
    verify(videoRepository).findBySubmittedBy(userId, pageable);
  }

  @Test
  void createVideo_youtubeIdExists_throwsAlreadyExists() {
    // Arrange
    when(youTubeService.extractVideoId("url")).thenReturn("abc123def45");
    when(videoRepository.existsByYoutubeId("abc123def45")).thenReturn(true);

    // Act
    Throwable thrown =
        catchThrowable(
            () ->
                videoService.createVideo(
                    "url", Set.of(Amendment.FIRST), Set.of(Participant.POLICE), null, userId));

    // Assert
    assertThat(thrown).isInstanceOf(VideoAlreadyExistsException.class);
  }

  @Test
  void createVideo_validRequest_savesMetadataAndSetsPending() {
    // Arrange
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

    // Act
    Video created =
        videoService.createVideo(
            "url",
            Set.of(Amendment.FIRST),
            Set.of(Participant.POLICE),
            LocalDate.of(2024, 1, 2),
            userId);

    // Assert
    assertThat(created.getYoutubeId()).isEqualTo("abc123def45");
    assertThat(created.getTitle()).isEqualTo("Title");
    assertThat(created.getStatus()).isEqualTo(VideoStatus.PENDING);
    assertThat(created.getSubmittedBy()).isEqualTo(userId);
  }

  @Test
  void updateVideo_notOwner_throwsUnauthorized() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(UUID.randomUUID());
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    // Act
    Throwable thrown =
        catchThrowable(
            () ->
                videoService.updateVideo(
                    videoId,
                    Set.of(Amendment.FOURTH),
                    Set.of(Participant.CITIZEN),
                    LocalDate.now(java.time.ZoneId.of("UTC")),
                    userId));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void updateVideo_owner_updatesFields() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    Video updated =
        videoService.updateVideo(
            videoId,
            Set.of(Amendment.SECOND),
            Set.of(Participant.GOVERNMENT),
            LocalDate.of(2024, 2, 2),
            userId);

    // Assert
    assertThat(updated.getAmendments()).containsExactly(Amendment.SECOND);
    assertThat(updated.getParticipants()).containsExactly(Participant.GOVERNMENT);
    assertThat(updated.getVideoDate()).isEqualTo(LocalDate.of(2024, 2, 2));
  }

  @Test
  void deleteVideo_owner_setsDeletedStatus() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    // Act
    videoService.deleteVideo(videoId, userId);

    // Assert
    verify(videoRepository).save(videoCaptor.capture());
    assertThat(videoCaptor.getValue().getStatus()).isEqualTo(VideoStatus.DELETED);
  }

  @Test
  void deleteVideo_notOwner_throwsUnauthorized() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(UUID.randomUUID());
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    // Act
    Throwable thrown = catchThrowable(() -> videoService.deleteVideo(videoId, userId));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void updateVideoInternal_validRequest_updatesFields() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    Video updated =
        videoService.updateVideoInternal(
            videoId,
            Set.of(Amendment.FIRST),
            Set.of(Participant.BUSINESS),
            LocalDate.of(2023, 3, 3));

    // Assert
    assertThat(updated.getAmendments()).containsExactly(Amendment.FIRST);
    assertThat(updated.getParticipants()).containsExactly(Participant.BUSINESS);
    assertThat(updated.getVideoDate()).isEqualTo(LocalDate.of(2023, 3, 3));
  }

  @Test
  void updateVideoStatus_validRequest_updatesStatus() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoRepository.save(any(Video.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    Video updated = videoService.updateVideoStatus(videoId, VideoStatus.APPROVED);

    // Assert
    assertThat(updated.getStatus()).isEqualTo(VideoStatus.APPROVED);
  }

  @Test
  void canView_variousStatuses_appliesVisibilityRules() {
    // Arrange
    Video approved = new Video();
    approved.setStatus(VideoStatus.APPROVED);

    Video pending = new Video();
    pending.setStatus(VideoStatus.PENDING);
    pending.setSubmittedBy(userId);

    Video rejected = new Video();
    rejected.setStatus(VideoStatus.REJECTED);
    rejected.setSubmittedBy(userId);

    Video deleted = new Video();
    deleted.setStatus(VideoStatus.DELETED);

    // Act
    boolean approvedVisible = videoService.canView(approved, null, null);
    boolean pendingVisibleToOwner = videoService.canView(pending, userId, null);
    boolean pendingVisibleToModerator = videoService.canView(pending, null, "MODERATOR");
    boolean pendingVisibleToUser = videoService.canView(pending, null, "USER");
    boolean rejectedVisibleToOwner = videoService.canView(rejected, userId, null);
    boolean rejectedVisibleToUser = videoService.canView(rejected, null, "USER");
    boolean deletedVisibleToAdmin = videoService.canView(deleted, null, "ADMIN");
    boolean deletedVisibleToModerator = videoService.canView(deleted, null, "MODERATOR");

    // Assert
    assertThat(approvedVisible).isTrue();
    assertThat(pendingVisibleToOwner).isTrue();
    assertThat(pendingVisibleToModerator).isTrue();
    assertThat(pendingVisibleToUser).isFalse();
    assertThat(rejectedVisibleToOwner).isTrue();
    assertThat(rejectedVisibleToUser).isFalse();
    assertThat(deletedVisibleToAdmin).isTrue();
    assertThat(deletedVisibleToModerator).isFalse();
  }
}
