package com.accountabilityatlas.videoservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Video;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VideoEventPublisherTest {

  private static final String VIDEO_EVENTS_QUEUE = "video-events";

  @Mock private SqsTemplate sqsTemplate;
  @InjectMocks private VideoEventPublisher videoEventPublisher;

  @Test
  void publishVideoSubmitted_validVideo_sendsToSqs() {
    // Arrange
    ReflectionTestUtils.setField(videoEventPublisher, "videoEventsQueue", VIDEO_EVENTS_QUEUE);
    Video video = new Video();
    video.setId(UUID.randomUUID());
    video.setTitle("Test Video");
    video.setSubmittedBy(UUID.randomUUID());
    video.setAmendments(Set.of(Amendment.FIRST, Amendment.FOURTH));
    String trustTier = "NEW";

    // Act
    videoEventPublisher.publishVideoSubmitted(video, trustTier, Collections.emptyList());

    // Assert
    ArgumentCaptor<VideoSubmittedEvent> captor = ArgumentCaptor.forClass(VideoSubmittedEvent.class);
    verify(sqsTemplate).send(eq(VIDEO_EVENTS_QUEUE), captor.capture());

    VideoSubmittedEvent event = captor.getValue();
    assertThat(event.videoId()).isEqualTo(video.getId());
    assertThat(event.submitterId()).isEqualTo(video.getSubmittedBy());
    assertThat(event.submitterTrustTier()).isEqualTo("NEW");
    assertThat(event.title()).isEqualTo("Test Video");
    assertThat(event.amendments()).containsExactlyInAnyOrder("FIRST", "FOURTH");
    assertThat(event.locationIds()).isEmpty();
    assertThat(event.timestamp()).isNotNull();
  }

  @Test
  void publishVideoSubmitted_withLocations_includesLocationsInEvent() {
    // Arrange
    ReflectionTestUtils.setField(videoEventPublisher, "videoEventsQueue", VIDEO_EVENTS_QUEUE);
    Video video = new Video();
    video.setId(UUID.randomUUID());
    video.setTitle("Test Video");
    video.setSubmittedBy(UUID.randomUUID());
    video.setAmendments(Set.of(Amendment.SECOND));
    UUID locationId = UUID.randomUUID();

    // Act
    videoEventPublisher.publishVideoSubmitted(video, "TRUSTED", java.util.List.of(locationId));

    // Assert
    ArgumentCaptor<VideoSubmittedEvent> captor = ArgumentCaptor.forClass(VideoSubmittedEvent.class);
    verify(sqsTemplate).send(eq(VIDEO_EVENTS_QUEUE), captor.capture());

    VideoSubmittedEvent event = captor.getValue();
    assertThat(event.submitterTrustTier()).isEqualTo("TRUSTED");
    assertThat(event.locationIds()).containsExactly(locationId);
  }
}
