package com.accountabilityatlas.videoservice.event;

import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Stream consumers for moderation events.
 *
 * <p>Handles VideoApproved and VideoRejected events from the moderation-events SQS queue.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ModerationEventHandler {

  private final VideoService videoService;

  /**
   * Handles VideoApproved events by updating video status to APPROVED.
   *
   * @return consumer for VideoApproved events
   */
  @Bean
  public Consumer<VideoApprovedEvent> handleVideoApproved() {
    return event -> {
      log.info(
          "Received VideoApproved event from SQS: videoId={}, reviewerId={}",
          event.videoId(),
          event.reviewerId());
      videoService.updateVideoStatus(event.videoId(), VideoStatus.APPROVED);
      log.info("Updated video {} status to APPROVED", event.videoId());
    };
  }

  /**
   * Handles VideoRejected events by updating video status to REJECTED.
   *
   * @return consumer for VideoRejected events
   */
  @Bean
  public Consumer<VideoRejectedEvent> handleVideoRejected() {
    return event -> {
      log.info(
          "Received VideoRejected event from SQS: videoId={}, reviewerId={}, reason={}",
          event.videoId(),
          event.reviewerId(),
          event.reason());
      videoService.updateVideoStatus(event.videoId(), VideoStatus.REJECTED);
      log.info("Updated video {} status to REJECTED (reason: {})", event.videoId(), event.reason());
    };
  }
}
