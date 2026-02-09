package com.accountabilityatlas.videoservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VideoTest {

  @Test
  void addAndRemoveLocation_updatesRelationship() {
    Video video = new Video();
    VideoLocation location = new VideoLocation();

    video.addLocation(location);

    assertThat(video.getLocations()).containsExactly(location);
    assertThat(location.getVideo()).isSameAs(video);

    video.removeLocation(location);

    assertThat(video.getLocations()).isEmpty();
    assertThat(location.getVideo()).isNull();
  }
}
