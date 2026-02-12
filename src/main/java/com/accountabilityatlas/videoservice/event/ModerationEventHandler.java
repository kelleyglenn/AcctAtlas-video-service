package com.accountabilityatlas.videoservice.event;

import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQS listener for moderation events.
 *
 * <p>Handles VideoApproved and VideoRejected events from the moderation-events SQS queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationEventHandler {

  private final VideoService videoService;

  /**
   * Handles moderation events by routing to the appropriate handler based on event type.
   *
   * @param event the moderation event (VideoApproved or VideoRejected)
   */
  @SqsListener("${app.sqs.moderation-events-queue:moderation-events}")
  public void handleModerationEvent(ModerationEvent event) {
    switch (event) {
      case VideoApprovedEvent approved -> handleVideoApproved(approved);
      case VideoRejectedEvent rejected -> handleVideoRejected(rejected);
    }
  }

  private void handleVideoApproved(VideoApprovedEvent event) {
    log.info(
        "Received VideoApproved event from SQS: videoId={}, reviewerId={}",
        event.videoId(),
        event.reviewerId());
    videoService.updateVideoStatus(event.videoId(), VideoStatus.APPROVED);
    log.info("Updated video {} status to APPROVED", event.videoId());
  }

  private void handleVideoRejected(VideoRejectedEvent event) {
    log.info(
        "Received VideoRejected event from SQS: videoId={}, reviewerId={}, reason={}",
        event.videoId(),
        event.reviewerId(),
        event.reason());
    videoService.updateVideoStatus(event.videoId(), VideoStatus.REJECTED);
    log.info("Updated video {} status to REJECTED (reason: {})", event.videoId(), event.reason());
  }
}
