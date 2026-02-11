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
  void handleVideoApproved_updatesStatusToApproved() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    VideoApprovedEvent event = new VideoApprovedEvent(videoId, reviewerId, Instant.now());

    Video video = new Video();
    video.setId(videoId);
    video.setStatus(VideoStatus.APPROVED);
    when(videoService.updateVideoStatus(videoId, VideoStatus.APPROVED)).thenReturn(video);

    // Act
    handler.handleVideoApproved().accept(event);

    // Assert
    verify(videoService).updateVideoStatus(videoId, VideoStatus.APPROVED);
  }

  @Test
  void handleVideoRejected_updatesStatusToRejected() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    VideoRejectedEvent event =
        new VideoRejectedEvent(videoId, reviewerId, "OFF_TOPIC", Instant.now());

    Video video = new Video();
    video.setId(videoId);
    video.setStatus(VideoStatus.REJECTED);
    when(videoService.updateVideoStatus(videoId, VideoStatus.REJECTED)).thenReturn(video);

    // Act
    handler.handleVideoRejected().accept(event);

    // Assert
    verify(videoService).updateVideoStatus(videoId, VideoStatus.REJECTED);
  }
}
