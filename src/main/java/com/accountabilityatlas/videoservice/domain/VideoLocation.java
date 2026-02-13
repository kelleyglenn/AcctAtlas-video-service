package com.accountabilityatlas.videoservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "video_locations", schema = "videos")
@Getter
@Setter
public class VideoLocation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_id", nullable = false)
  private Video video;

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  @Column(name = "display_name")
  private String displayName;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  private Double latitude;

  private Double longitude;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Setter(AccessLevel.NONE)
  @Column(
      name = "sys_period",
      insertable = false,
      updatable = false,
      columnDefinition = "tstzrange")
  private String sysPeriod;
}
