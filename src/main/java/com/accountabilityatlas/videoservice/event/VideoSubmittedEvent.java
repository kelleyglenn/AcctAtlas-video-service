package com.accountabilityatlas.videoservice.event;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Event published when a video is submitted for review. */
public record VideoSubmittedEvent(
    UUID videoId,
    UUID submitterId,
    String submitterTrustTier,
    String title,
    Set<String> amendments,
    List<UUID> locationIds,
    Instant timestamp) {}
