package com.accountabilityatlas.videoservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Video;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class VideoEventPublisherTest {

  @Mock private ApplicationEventPublisher applicationEventPublisher;
  private VideoEventPublisher videoEventPublisher;

  @BeforeEach
  void setUp() {
    videoEventPublisher = new VideoEventPublisher(applicationEventPublisher);
  }

  @Test
  void publishVideoSubmitted_validVideo_publishesEvent() {
    // Arrange
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
    verify(applicationEventPublisher).publishEvent(captor.capture());

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
    verify(applicationEventPublisher).publishEvent(captor.capture());

    VideoSubmittedEvent event = captor.getValue();
    assertThat(event.submitterTrustTier()).isEqualTo("TRUSTED");
    assertThat(event.locationIds()).containsExactly(locationId);
  }
}
