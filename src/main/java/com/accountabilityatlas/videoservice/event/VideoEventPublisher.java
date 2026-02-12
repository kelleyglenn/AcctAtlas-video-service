package com.accountabilityatlas.videoservice.event;

import com.accountabilityatlas.videoservice.domain.Amendment;
import com.accountabilityatlas.videoservice.domain.Video;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Publishes video-related events to SQS via AWS Spring SqsTemplate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoEventPublisher {

  private final SqsTemplate sqsTemplate;

  @Value("${app.sqs.video-events-queue:video-events}")
  private String videoEventsQueue;

  /**
   * Publishes a VideoSubmitted event to the video-events queue.
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

    log.info(
        "Publishing VideoSubmitted event for video {} to SQS queue {}",
        video.getId(),
        videoEventsQueue);
    try {
      sqsTemplate.send(videoEventsQueue, event);
      log.debug("Published VideoSubmitted event: {}", event);
    } catch (Exception e) {
      log.error(
          "Failed to publish VideoSubmitted event for video {}: {}",
          video.getId(),
          e.getMessage(),
          e);
      throw e;
    }
  }
}
