package com.accountabilityatlas.videoservice.event;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Video;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes video-related events using Spring's ApplicationEventPublisher. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  /**
   * Publishes a VideoSubmitted event.
   *
   * @param video the submitted video
   * @param submitterTrustTier the submitter's trust tier
   * @param locationIds list of location IDs associated with the video
   */
  public void publishVideoSubmitted(
      Video video, String submitterTrustTier, List<UUID> locationIds) {
    VideoSubmittedEvent event =
        new VideoSubmittedEvent(
            video.getId(),
            video.getSubmittedBy(),
            submitterTrustTier,
            video.getTitle(),
            video.getAmendments().stream().map(Amendment::name).collect(Collectors.toSet()),
            locationIds,
            Instant.now());

    log.info("Publishing VideoSubmitted event for video {}", video.getId());
    applicationEventPublisher.publishEvent(event);
    log.debug("Published VideoSubmitted event: {}", event);
  }
}
