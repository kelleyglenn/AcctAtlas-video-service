package com.accountabilityatlas.videoservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "videos", schema = "videos")
@Getter
@Setter
public class Video {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "youtube_id", nullable = false, unique = true, length = 11)
  private String youtubeId;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Column(name = "channel_id", length = 50)
  private String channelId;

  @Column(name = "channel_name", length = 255)
  private String channelName;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "video_date")
  private LocalDate videoDate;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "video_amendments",
      schema = "videos",
      joinColumns = @JoinColumn(name = "video_id"))
  @Column(name = "amendment")
  @Enumerated(EnumType.STRING)
  private Set<Amendment> amendments = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "video_participants",
      schema = "videos",
      joinColumns = @JoinColumn(name = "video_id"))
  @Column(name = "participant")
  @Enumerated(EnumType.STRING)
  private Set<Participant> participants = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VideoStatus status = VideoStatus.PENDING;

  @Column(name = "submitted_by", nullable = false)
  private UUID submittedBy;

  @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<VideoLocation> locations = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Setter(AccessLevel.NONE)
  @JdbcTypeCode(SqlTypes.OTHER)
  @Column(
      name = "sys_period",
      insertable = false,
      updatable = false,
      columnDefinition = "tstzrange")
  private String sysPeriod;

  public void addLocation(VideoLocation location) {
    locations.add(location);
    location.setVideo(this);
  }

  public void removeLocation(VideoLocation location) {
    locations.remove(location);
    location.setVideo(null);
  }
}
