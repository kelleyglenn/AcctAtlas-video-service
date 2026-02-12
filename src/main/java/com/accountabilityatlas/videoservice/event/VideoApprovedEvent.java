package com.accountabilityatlas.videoservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received when a video passes moderation.
 *
 * @param videoId the ID of the approved video
 * @param reviewerId the ID of the moderator who approved the video
 * @param timestamp when the approval occurred
 */
public record VideoApprovedEvent(UUID videoId, UUID reviewerId, Instant timestamp)
    implements ModerationEvent {}
