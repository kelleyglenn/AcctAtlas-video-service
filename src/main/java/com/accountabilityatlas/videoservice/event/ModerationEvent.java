package com.accountabilityatlas.videoservice.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

/**
 * Sealed interface for moderation events received from SQS.
 *
 * <p>Uses Jackson type info for polymorphic deserialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = VideoApprovedEvent.class, name = "VIDEO_APPROVED"),
  @JsonSubTypes.Type(value = VideoRejectedEvent.class, name = "VIDEO_REJECTED")
})
public sealed interface ModerationEvent permits VideoApprovedEvent, VideoRejectedEvent {
  UUID videoId();
}
