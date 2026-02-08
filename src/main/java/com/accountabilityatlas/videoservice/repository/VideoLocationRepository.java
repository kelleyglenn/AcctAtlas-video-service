package com.accountabilityatlas.videoservice.repository;

import com.accountabilityatlas.videoservice.domain.VideoLocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoLocationRepository extends JpaRepository<VideoLocation, UUID> {

  List<VideoLocation> findByVideoId(UUID videoId);

  Optional<VideoLocation> findByVideoIdAndLocationId(UUID videoId, UUID locationId);

  boolean existsByVideoIdAndLocationId(UUID videoId, UUID locationId);

  @Modifying
  @Query("UPDATE VideoLocation vl SET vl.primary = false WHERE vl.video.id = :videoId")
  void clearPrimaryForVideo(UUID videoId);

  Optional<VideoLocation> findByVideoIdAndPrimaryTrue(UUID videoId);
}
