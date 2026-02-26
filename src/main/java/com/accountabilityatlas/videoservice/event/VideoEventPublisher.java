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

  @Value("${app.sqs.video-status-events-queue:video-status-events}")
  private String videoStatusEventsQueue;

  @Value("${app.sqs.user-video-events-queue:user-video-events}")
  private String userVideoEventsQueue;

  @Value("${app.sqs.user-video-status-events-queue:user-video-status-events}")
  private String userVideoStatusEventsQueue;

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
      sqsTemplate.send(userVideoEventsQueue, event);
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

  /**
   * Publishes a VideoStatusChanged event to the video-status-events and user-video-status-events
   * queues.
   *
   * @param videoId the video ID
   * @param submittedBy the submitter's user ID
   * @param locationIds list of location IDs associated with the video
   * @param previousStatus the previous status (as String to avoid cross-service coupling)
   * @param newStatus the new status
   */
  public void publishVideoStatusChanged(
      UUID videoId,
      UUID submittedBy,
      List<UUID> locationIds,
      String previousStatus,
      String newStatus) {
    VideoStatusChangedEvent event =
        new VideoStatusChangedEvent(
            videoId, submittedBy, locationIds, previousStatus, newStatus, Instant.now());

    log.info(
        "Publishing VideoStatusChanged event for video {} ({} -> {}) to SQS queue {}",
        videoId,
        previousStatus,
        newStatus,
        videoStatusEventsQueue);
    try {
      sqsTemplate.send(videoStatusEventsQueue, event);
      sqsTemplate.send(userVideoStatusEventsQueue, event);
      log.debug("Published VideoStatusChanged event: {}", event);
    } catch (Exception e) {
      log.error(
          "Failed to publish VideoStatusChanged event for video {}: {}",
          videoId,
          e.getMessage(),
          e);
      throw e;
    }
  }
}
