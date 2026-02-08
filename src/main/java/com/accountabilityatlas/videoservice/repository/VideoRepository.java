package com.accountabilityatlas.videoservice.repository;

import com.accountabilityatlas.videoservice.domain.Video;
import com.accountabilityatlas.videoservice.domain.VideoStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

  Optional<Video> findByYoutubeId(String youtubeId);

  boolean existsByYoutubeId(String youtubeId);

  Page<Video> findByStatus(VideoStatus status, Pageable pageable);

  Page<Video> findBySubmittedBy(UUID submittedBy, Pageable pageable);

  Page<Video> findBySubmittedByAndStatus(UUID submittedBy, VideoStatus status, Pageable pageable);

  @Query(
      """
      SELECT v FROM Video v
      WHERE v.status = :status
      ORDER BY v.createdAt DESC
      """)
  Page<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);
}
