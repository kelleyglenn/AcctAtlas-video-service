package com.accountabilityatlas.videoservice.service;

import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.LocationAlreadyLinkedException;
import com.accountabilityatlas.videoservice.exception.LocationNotFoundException;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.exception.VideoNotFoundException;
import com.accountabilityatlas.videoservice.repository.VideoLocationRepository;
import com.accountabilityatlas.videoservice.repository.VideoRepository;
import com.accountabilityatlas.videoservice.service.LocationClient.LocationSummary;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoLocationService {

  private final VideoRepository videoRepository;
  private final VideoLocationRepository videoLocationRepository;
  private final LocationClient locationClient;

  @Transactional(readOnly = true)
  public List<VideoLocation> getVideoLocations(UUID videoId) {
    if (!videoRepository.existsById(videoId)) {
      throw new VideoNotFoundException(videoId);
    }
    return videoLocationRepository.findByVideoId(videoId);
  }

  @Transactional
  public VideoLocation addLocation(
      UUID videoId, UUID locationId, boolean isPrimary, UUID currentUserId) {

    Video video =
        videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));

    if (!canModify(video, currentUserId)) {
      throw new UnauthorizedException("You do not have permission to modify this video");
    }

    return addLocationInternal(video, locationId, isPrimary);
  }

  @Transactional
  public VideoLocation addLocationInternal(UUID videoId, UUID locationId, boolean isPrimary) {
    Video video =
        videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
    return addLocationInternal(video, locationId, isPrimary);
  }

  private VideoLocation addLocationInternal(Video video, UUID locationId, boolean isPrimary) {
    if (videoLocationRepository.existsByVideoIdAndLocationId(video.getId(), locationId)) {
      throw new LocationAlreadyLinkedException(video.getId(), locationId);
    }

    LocationSummary location =
        locationClient
            .getLocation(locationId)
            .orElseThrow(() -> new LocationNotFoundException(locationId));

    if (isPrimary) {
      videoLocationRepository.clearPrimaryForVideo(video.getId());
    }

    VideoLocation videoLocation = new VideoLocation();
    videoLocation.setVideo(video);
    videoLocation.setLocationId(locationId);
    videoLocation.setPrimary(isPrimary);
    videoLocation.setDisplayName(location.displayName());
    videoLocation.setCity(location.city());
    videoLocation.setState(location.state());
    if (location.coordinates() != null) {
      videoLocation.setLatitude(location.coordinates().latitude());
      videoLocation.setLongitude(location.coordinates().longitude());
    }

    return videoLocationRepository.save(videoLocation);
  }

  @Transactional
  public void removeLocation(UUID videoId, UUID locationId, UUID currentUserId) {
    Video video =
        videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));

    if (!canModify(video, currentUserId)) {
      throw new UnauthorizedException("You do not have permission to modify this video");
    }

    removeLocationInternal(videoId, locationId);
  }

  @Transactional
  public void removeLocationInternal(UUID videoId, UUID locationId) {
    VideoLocation location =
        videoLocationRepository
            .findByVideoIdAndLocationId(videoId, locationId)
            .orElseThrow(() -> new LocationNotFoundException(locationId));

    videoLocationRepository.delete(location);
  }

  private boolean canModify(Video video, UUID currentUserId) {
    if (!video.getSubmittedBy().equals(currentUserId)) {
      return false;
    }
    return video.getStatus() != VideoStatus.APPROVED;
  }
}
