package com.accountabilityatlas.videoservice.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationEventHandlerTest {

  @Mock private VideoService videoService;

  private ModerationEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ModerationEventHandler(videoService);
  }

  @Test
  void handleModerationEvent_videoApproved_updatesStatusToApproved() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    VideoApprovedEvent event = new VideoApprovedEvent(videoId, reviewerId, Instant.now());

    Video video = new Video();
    video.setId(videoId);
    video.setStatus(VideoStatus.APPROVED);
    when(videoService.updateVideoStatus(videoId, VideoStatus.APPROVED)).thenReturn(video);

    // Act
    handler.handleModerationEvent(event);

    // Assert
    verify(videoService).updateVideoStatus(videoId, VideoStatus.APPROVED);
  }

  @Test
  void handleModerationEvent_videoRejected_updatesStatusToRejected() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    VideoRejectedEvent event =
        new VideoRejectedEvent(videoId, reviewerId, "OFF_TOPIC", Instant.now());

    Video video = new Video();
    video.setId(videoId);
    video.setStatus(VideoStatus.REJECTED);
    when(videoService.updateVideoStatus(videoId, VideoStatus.REJECTED, "OFF_TOPIC"))
        .thenReturn(video);

    // Act
    handler.handleModerationEvent(event);

    // Assert
    verify(videoService).updateVideoStatus(videoId, VideoStatus.REJECTED, "OFF_TOPIC");
  }

  @Test
  void handleModerationEvent_videoRejected_storesRejectionReason() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    String reason = "Duplicate submission";
    var event = new VideoRejectedEvent(videoId, UUID.randomUUID(), reason, Instant.now());

    var video = new Video();
    video.setId(videoId);
    video.setStatus(VideoStatus.REJECTED);
    video.setRejectionReason(reason);
    when(videoService.updateVideoStatus(videoId, VideoStatus.REJECTED, reason)).thenReturn(video);

    // Act
    handler.handleModerationEvent(event);

    // Assert
    verify(videoService).updateVideoStatus(videoId, VideoStatus.REJECTED, reason);
  }
}
