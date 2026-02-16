package com.accountabilityatlas.videoservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoLocation;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import com.accountabilityatlas.videoservice.service.VideoLocationService;
import com.accountabilityatlas.videoservice.service.VideoService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalVideoControllerTest {

  @Mock private VideoService videoService;
  @Mock private VideoLocationService videoLocationService;

  @InjectMocks private InternalVideoController controller;

  @Test
  void updateVideo_validRequest_callsService() {
    // Arrange
    UUID id = UUID.randomUUID();
    Video video = new Video();
    when(videoService.updateVideoInternal(eq(id), anySet(), anySet(), any())).thenReturn(video);

    var request =
        new InternalVideoController.UpdateVideoRequest(
            Set.of("FIRST"), Set.of("POLICE"), LocalDate.of(2024, 1, 1));

    // Act
    var response = controller.updateVideo(id, request);

    // Assert
    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(videoService).updateVideoInternal(eq(id), anySet(), anySet(), any());
  }

  @Test
  void updateStatus_validRequest_callsService() {
    // Arrange
    UUID id = UUID.randomUUID();
    Video video = new Video();
    when(videoService.updateVideoStatus(id, VideoStatus.APPROVED)).thenReturn(video);

    var request = new InternalVideoController.UpdateStatusRequest("APPROVED");

    // Act
    var response = controller.updateStatus(id, request);

    // Assert
    assertThat(response.getStatusCode().value()).isEqualTo(204);
  }

  @Test
  void addLocation_validRequest_callsService() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    VideoLocation location = new VideoLocation();
    when(videoLocationService.addLocationInternal(id, locationId, true)).thenReturn(location);

    var request = new InternalVideoController.AddLocationRequest(locationId, true);

    // Act
    VideoLocation response = controller.addLocation(id, request).getBody();

    // Assert
    assertThat(response).isSameAs(location);
    verify(videoLocationService).addLocationInternal(id, locationId, true);
  }

  @Test
  void removeLocation_validRequest_callsService() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();

    // Act
    controller.removeLocation(id, locationId);

    // Assert
    verify(videoLocationService).removeLocationInternal(id, locationId);
  }
}
