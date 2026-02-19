package com.accountabilityatlas.videoservice.service;

import com.accountabilityatlas.videoservice.domain.*;
import com.accountabilityatlas.videoservice.event.VideoEventPublisher;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.exception.VideoAlreadyExistsException;
import com.accountabilityatlas.videoservice.exception.VideoNotFoundException;
import com.accountabilityatlas.videoservice.repository.VideoRepository;
import com.accountabilityatlas.videoservice.service.YouTubeService.YouTubeMetadata;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoService {

  private final VideoRepository videoRepository;
  private final YouTubeService youTubeService;
  private final VideoEventPublisher videoEventPublisher;

  @Transactional(readOnly = true)
  public Video getVideo(UUID id) {
    return videoRepository
        .findByIdWithLocations(id)
        .orElseThrow(() -> new VideoNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public Optional<Video> findByYoutubeId(String youtubeId) {
    return videoRepository.findByYoutubeId(youtubeId);
  }

  @Transactional(readOnly = true)
  public Page<Video> listVideos(VideoStatus status, Pageable pageable) {
    if (status == null) {
      status = VideoStatus.APPROVED;
    }
    return videoRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Video> listVideosByUser(UUID userId, VideoStatus status, Pageable pageable) {
    if (status != null) {
      return videoRepository.findBySubmittedByAndStatus(userId, status, pageable);
    }
    return videoRepository.findBySubmittedBy(userId, pageable);
  }

  /**
   * Creates a video and publishes a VideoSubmitted event.
   *
   * @param youtubeUrl the YouTube URL
   * @param amendments set of amendments
   * @param participants set of participants
   * @param videoDate the incident date
   * @param submittedBy the submitter's user ID
   * @param submitterTrustTier the submitter's trust tier
   * @param locationIds list of location IDs
   * @return the created video
   */
  @Transactional
  public Video createVideo(
      String youtubeUrl,
      Set<Amendment> amendments,
      Set<Participant> participants,
      LocalDate videoDate,
      UUID submittedBy,
      String submitterTrustTier,
      List<UUID> locationIds) {

    String videoId = youTubeService.extractVideoId(youtubeUrl);

    if (videoRepository.existsByYoutubeId(videoId)) {
      throw new VideoAlreadyExistsException(videoId);
    }

    YouTubeMetadata metadata = youTubeService.fetchMetadata(videoId);

    Video video = new Video();
    video.setYoutubeId(videoId);
    video.setTitle(metadata.title());
    video.setDescription(metadata.description());
    video.setThumbnailUrl(metadata.thumbnailUrl());
    video.setDurationSeconds(metadata.durationSeconds());
    video.setChannelId(metadata.channelId());
    video.setChannelName(metadata.channelName());
    video.setPublishedAt(metadata.publishedAt());
    video.setVideoDate(videoDate);
    video.setAmendments(amendments);
    video.setParticipants(participants);
    video.setStatus(VideoStatus.PENDING);
    video.setSubmittedBy(submittedBy);

    Video saved = videoRepository.save(video);

    // Publish event after successful save
    videoEventPublisher.publishVideoSubmitted(saved, submitterTrustTier, locationIds);

    return saved;
  }

  @Transactional
  public Video updateVideo(
      UUID id,
      Set<Amendment> amendments,
      Set<Participant> participants,
      LocalDate videoDate,
      UUID currentUserId) {

    Video video = findVideoOrThrow(id);

    if (mayNotModify(video, currentUserId)) {
      throw new UnauthorizedException("You do not have permission to modify this video");
    }

    if (amendments != null && !amendments.isEmpty()) {
      video.setAmendments(amendments);
    }
    if (participants != null && !participants.isEmpty()) {
      video.setParticipants(participants);
    }
    if (videoDate != null) {
      video.setVideoDate(videoDate);
    }

    return videoRepository.save(video);
  }

  @Transactional
  public void deleteVideo(UUID id, UUID currentUserId) {
    Video video = findVideoOrThrow(id);

    if (mayNotModify(video, currentUserId)) {
      throw new UnauthorizedException("You do not have permission to delete this video");
    }

    video.setStatus(VideoStatus.DELETED);
    videoRepository.save(video);
  }

  @Transactional
  public Video updateVideoInternal(
      UUID id, Set<Amendment> amendments, Set<Participant> participants, LocalDate videoDate) {

    Video video = findVideoOrThrow(id);

    if (amendments != null && !amendments.isEmpty()) {
      video.setAmendments(amendments);
    }
    if (participants != null && !participants.isEmpty()) {
      video.setParticipants(participants);
    }
    if (videoDate != null) {
      video.setVideoDate(videoDate);
    }

    return videoRepository.save(video);
  }

  @Transactional
  public Video updateVideoStatus(UUID id, VideoStatus newStatus, String rejectionReason) {
    Video video =
        videoRepository.findByIdWithLocations(id).orElseThrow(() -> new VideoNotFoundException(id));
    VideoStatus previousStatus = video.getStatus();
    video.setStatus(newStatus);
    if (rejectionReason != null) {
      video.setRejectionReason(rejectionReason);
    }
    Video saved = videoRepository.save(video);

    // Publish status change event for location-service to update video counts
    List<UUID> locationIds =
        saved.getLocations().stream().map(VideoLocation::getLocationId).toList();
    if (!locationIds.isEmpty()
        && ((newStatus == VideoStatus.APPROVED && previousStatus != VideoStatus.APPROVED)
            || (previousStatus == VideoStatus.APPROVED && newStatus != VideoStatus.APPROVED))) {
      videoEventPublisher.publishVideoStatusChanged(
          saved.getId(), locationIds, previousStatus.name(), newStatus.name());
    }

    return saved;
  }

  private Video findVideoOrThrow(UUID id) {
    return videoRepository.findById(id).orElseThrow(() -> new VideoNotFoundException(id));
  }

  private boolean mayNotModify(Video video, UUID currentUserId) {
    if (!video.getSubmittedBy().equals(currentUserId)) {
      return true;
    }
    return video.getStatus() == VideoStatus.APPROVED;
  }

  public boolean canView(Video video, UUID currentUserId, String trustTier) {
    return switch (video.getStatus()) {
      case APPROVED -> true;
      case PENDING, REJECTED -> isOwnerOrModerator(video, currentUserId, trustTier);
      case DELETED -> "ADMIN".equals(trustTier);
    };
  }

  private boolean isOwnerOrModerator(Video video, UUID currentUserId, String trustTier) {
    if (video.getSubmittedBy().equals(currentUserId)) {
      return true;
    }
    return Set.of("MODERATOR", "ADMIN").contains(trustTier);
  }
}
