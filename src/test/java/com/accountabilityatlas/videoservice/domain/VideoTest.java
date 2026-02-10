package com.accountabilityatlas.videoservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VideoTest {

  @Test
  void addAndRemoveLocation_validCalls_updatesRelationship() {
    // Arrange
    Video video = new Video();
    VideoLocation location = new VideoLocation();

    // Act
    video.addLocation(location);

    // Assert
    assertThat(video.getLocations()).containsExactly(location);
    assertThat(location.getVideo()).isSameAs(video);

    // Act
    video.removeLocation(location);

    // Assert
    assertThat(video.getLocations()).isEmpty();
    assertThat(location.getVideo()).isNull();
  }
}
