package com.accountabilityatlas.videoservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received when a video fails moderation.
 *
 * @param videoId the ID of the rejected video
 * @param reviewerId the ID of the moderator who rejected the video
 * @param reason the reason for rejection
 * @param timestamp when the rejection occurred
 */
public record VideoRejectedEvent(
    UUID videoId, UUID reviewerId, String reason, Instant timestamp) {}
