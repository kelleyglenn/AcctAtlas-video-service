package com.accountabilityatlas.videoservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Video;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.Collections;
import java.util.List;
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
  private static final String VIDEO_STATUS_EVENTS_QUEUE = "video-status-events";
  private static final String USER_VIDEO_EVENTS_QUEUE = "user-video-events";
  private static final String USER_VIDEO_STATUS_EVENTS_QUEUE = "user-video-status-events";

  @Mock private SqsTemplate sqsTemplate;
  @InjectMocks private VideoEventPublisher videoEventPublisher;

  @Test
  void publishVideoSubmitted_validVideo_sendsToSqs() {
    // Arrange
    ReflectionTestUtils.setField(videoEventPublisher, "videoEventsQueue", VIDEO_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoEventsQueue", USER_VIDEO_EVENTS_QUEUE);
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
    verify(sqsTemplate).send(eq(USER_VIDEO_EVENTS_QUEUE), any(VideoSubmittedEvent.class));
  }

  @Test
  void publishVideoSubmitted_withLocations_includesLocationsInEvent() {
    // Arrange
    ReflectionTestUtils.setField(videoEventPublisher, "videoEventsQueue", VIDEO_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoEventsQueue", USER_VIDEO_EVENTS_QUEUE);
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
    verify(sqsTemplate).send(eq(USER_VIDEO_EVENTS_QUEUE), any(VideoSubmittedEvent.class));
  }

  @Test
  void publishVideoSubmitted_sqsFailure_rethrowsException() {
    // Arrange
    ReflectionTestUtils.setField(videoEventPublisher, "videoEventsQueue", VIDEO_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoEventsQueue", USER_VIDEO_EVENTS_QUEUE);
    Video video = new Video();
    video.setId(UUID.randomUUID());
    video.setTitle("Test Video");
    video.setSubmittedBy(UUID.randomUUID());
    video.setAmendments(Set.of(Amendment.FIRST));

    RuntimeException sqsException = new RuntimeException("SQS connection failed");
    when(sqsTemplate.send(eq(VIDEO_EVENTS_QUEUE), any(VideoSubmittedEvent.class)))
        .thenThrow(sqsException);

    // Act & Assert
    assertThatThrownBy(
            () ->
                videoEventPublisher.publishVideoSubmitted(
                    video, "NEW", java.util.Collections.emptyList()))
        .isSameAs(sqsException);
    verify(sqsTemplate, times(1)).send(any(String.class), any(VideoSubmittedEvent.class));
  }

  @Test
  void publishVideoStatusChanged_validEvent_sendsToSqs() {
    // Arrange
    ReflectionTestUtils.setField(
        videoEventPublisher, "videoStatusEventsQueue", VIDEO_STATUS_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoStatusEventsQueue", USER_VIDEO_STATUS_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID submittedBy = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();

    // Act
    videoEventPublisher.publishVideoStatusChanged(
        videoId, submittedBy, List.of(locationId), "PENDING", "APPROVED");

    // Assert
    ArgumentCaptor<VideoStatusChangedEvent> captor =
        ArgumentCaptor.forClass(VideoStatusChangedEvent.class);
    verify(sqsTemplate).send(eq(VIDEO_STATUS_EVENTS_QUEUE), captor.capture());

    VideoStatusChangedEvent event = captor.getValue();
    assertThat(event.videoId()).isEqualTo(videoId);
    assertThat(event.submittedBy()).isEqualTo(submittedBy);
    assertThat(event.locationIds()).containsExactly(locationId);
    assertThat(event.previousStatus()).isEqualTo("PENDING");
    assertThat(event.newStatus()).isEqualTo("APPROVED");
    assertThat(event.timestamp()).isNotNull();
    verify(sqsTemplate)
        .send(eq(USER_VIDEO_STATUS_EVENTS_QUEUE), any(VideoStatusChangedEvent.class));
  }

  @Test
  void publishVideoStatusChanged_multipleLocations_includesAllInEvent() {
    // Arrange
    ReflectionTestUtils.setField(
        videoEventPublisher, "videoStatusEventsQueue", VIDEO_STATUS_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoStatusEventsQueue", USER_VIDEO_STATUS_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID submittedBy = UUID.randomUUID();
    UUID loc1 = UUID.randomUUID();
    UUID loc2 = UUID.randomUUID();

    // Act
    videoEventPublisher.publishVideoStatusChanged(
        videoId, submittedBy, List.of(loc1, loc2), "APPROVED", "REJECTED");

    // Assert
    ArgumentCaptor<VideoStatusChangedEvent> captor =
        ArgumentCaptor.forClass(VideoStatusChangedEvent.class);
    verify(sqsTemplate).send(eq(VIDEO_STATUS_EVENTS_QUEUE), captor.capture());

    VideoStatusChangedEvent event = captor.getValue();
    assertThat(event.submittedBy()).isEqualTo(submittedBy);
    assertThat(event.locationIds()).containsExactly(loc1, loc2);
    assertThat(event.previousStatus()).isEqualTo("APPROVED");
    assertThat(event.newStatus()).isEqualTo("REJECTED");
    verify(sqsTemplate)
        .send(eq(USER_VIDEO_STATUS_EVENTS_QUEUE), any(VideoStatusChangedEvent.class));
  }

  @Test
  void publishVideoStatusChanged_sqsFailure_rethrowsException() {
    // Arrange
    ReflectionTestUtils.setField(
        videoEventPublisher, "videoStatusEventsQueue", VIDEO_STATUS_EVENTS_QUEUE);
    ReflectionTestUtils.setField(
        videoEventPublisher, "userVideoStatusEventsQueue", USER_VIDEO_STATUS_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID submittedBy = UUID.randomUUID();

    RuntimeException sqsException = new RuntimeException("SQS connection failed");
    when(sqsTemplate.send(eq(VIDEO_STATUS_EVENTS_QUEUE), any(VideoStatusChangedEvent.class)))
        .thenThrow(sqsException);

    // Act & Assert
    assertThatThrownBy(
            () ->
                videoEventPublisher.publishVideoStatusChanged(
                    videoId, submittedBy, List.of(UUID.randomUUID()), "PENDING", "APPROVED"))
        .isSameAs(sqsException);
    verify(sqsTemplate, times(1)).send(any(String.class), any(VideoStatusChangedEvent.class));
  }
}
