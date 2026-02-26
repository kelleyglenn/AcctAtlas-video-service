package com.accountabilityatlas.videoservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VideoStatusChangedEvent(
    UUID videoId,
    UUID submittedBy,
    List<UUID> locationIds,
    String previousStatus,
    String newStatus,
    Instant timestamp) {}
