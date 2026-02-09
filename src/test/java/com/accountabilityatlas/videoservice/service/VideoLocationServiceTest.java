package com.accountabilityatlas.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.exception.LocationAlreadyLinkedException;
import com.accountabilityatlas.videoservice.exception.LocationNotFoundException;
import com.accountabilityatlas.videoservice.exception.UnauthorizedException;
import com.accountabilityatlas.videoservice.exception.VideoNotFoundException;
import com.accountabilityatlas.videoservice.repository.VideoLocationRepository;
import com.accountabilityatlas.videoservice.repository.VideoRepository;
import com.accountabilityatlas.videoservice.service.LocationClient.Coordinates;
import com.accountabilityatlas.videoservice.service.LocationClient.LocationSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoLocationServiceTest {

  @Mock private VideoRepository videoRepository;
  @Mock private VideoLocationRepository videoLocationRepository;
  @Mock private LocationClient locationClient;

  @InjectMocks private VideoLocationService videoLocationService;

  private UUID videoId;
  private UUID locationId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    videoId = UUID.randomUUID();
    locationId = UUID.randomUUID();
    userId = UUID.randomUUID();
  }

  @Test
  void getVideoLocations_videoMissing_throwsNotFound() {
    // Arrange
    when(videoRepository.existsById(videoId)).thenReturn(false);

    // Act
    Throwable thrown = catchThrowable(() -> videoLocationService.getVideoLocations(videoId));

    // Assert
    assertThat(thrown).isInstanceOf(VideoNotFoundException.class);
  }

  @Test
  void getVideoLocations_videoExists_returnsLocations() {
    // Arrange
    when(videoRepository.existsById(videoId)).thenReturn(true);
    when(videoLocationRepository.findByVideoId(videoId)).thenReturn(List.of(new VideoLocation()));

    // Act
    List<VideoLocation> result = videoLocationService.getVideoLocations(videoId);

    // Assert
    assertThat(result).hasSize(1);
  }

  @Test
  void addLocation_notOwner_throwsUnauthorized() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(UUID.randomUUID());
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    // Act
    Throwable thrown =
        catchThrowable(() -> videoLocationService.addLocation(videoId, locationId, false, userId));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void addLocation_alreadyLinked_throwsConflict() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoLocationRepository.existsByVideoIdAndLocationId(videoId, locationId))
        .thenReturn(true);

    // Act
    Throwable thrown =
        catchThrowable(() -> videoLocationService.addLocation(videoId, locationId, false, userId));

    // Assert
    assertThat(thrown).isInstanceOf(LocationAlreadyLinkedException.class);
  }

  @Test
  void addLocation_locationMissing_throwsNotFound() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoLocationRepository.existsByVideoIdAndLocationId(videoId, locationId))
        .thenReturn(false);
    when(locationClient.getLocation(locationId)).thenReturn(Optional.empty());

    // Act
    Throwable thrown =
        catchThrowable(() -> videoLocationService.addLocation(videoId, locationId, false, userId));

    // Assert
    assertThat(thrown).isInstanceOf(LocationNotFoundException.class);
  }

  @Test
  void addLocation_primaryRequested_setsPrimaryAndCaches() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(userId);
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
    when(videoLocationRepository.existsByVideoIdAndLocationId(videoId, locationId))
        .thenReturn(false);
    when(locationClient.getLocation(locationId))
        .thenReturn(
            Optional.of(
                new LocationSummary(
                    locationId, "Display", "City", "State", new Coordinates(1.0, 2.0))));
    when(videoLocationRepository.save(any(VideoLocation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    VideoLocation saved = videoLocationService.addLocation(videoId, locationId, true, userId);

    // Assert
    verify(videoLocationRepository).clearPrimaryForVideo(videoId);
    assertThat(saved.getDisplayName()).isEqualTo("Display");
    assertThat(saved.isPrimary()).isTrue();
  }

  @Test
  void removeLocation_notOwner_throwsUnauthorized() {
    // Arrange
    Video video = new Video();
    video.setId(videoId);
    video.setSubmittedBy(UUID.randomUUID());
    video.setStatus(VideoStatus.PENDING);
    when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));

    // Act
    Throwable thrown =
        catchThrowable(() -> videoLocationService.removeLocation(videoId, locationId, userId));

    // Assert
    assertThat(thrown).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void removeLocationInternal_missingLink_throwsNotFound() {
    // Arrange
    when(videoLocationRepository.findByVideoIdAndLocationId(videoId, locationId))
        .thenReturn(Optional.empty());

    // Act
    Throwable thrown =
        catchThrowable(() -> videoLocationService.removeLocationInternal(videoId, locationId));

    // Assert
    assertThat(thrown).isInstanceOf(LocationNotFoundException.class);
  }

  @Test
  void removeLocationInternal_existingLink_deletesLocation() {
    // Arrange
    VideoLocation location = new VideoLocation();
    when(videoLocationRepository.findByVideoIdAndLocationId(videoId, locationId))
        .thenReturn(Optional.of(location));

    // Act
    videoLocationService.removeLocationInternal(videoId, locationId);

    // Assert
    verify(videoLocationRepository).delete(location);
  }
}
