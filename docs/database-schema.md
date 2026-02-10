# Video Service - Database Schema

## Overview

This document describes the database schema for the Video Service, focusing on JPA entity mappings and service-specific implementation details.

### Tables Owned by Video Service

| Table | Temporal | Description |
|-------|----------|-------------|
| `videos.videos` | Yes | Core video metadata, YouTube info, status |
| `videos.video_amendments` | No | Element collection for amendments |
| `videos.video_participants` | No | Element collection for participants |
| `videos.video_locations` | Yes | Video-to-location associations with cached location data |

---

## JPA Entity Mappings

### Video Entity

```java
@Entity
@Table(name = "videos", schema = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "youtube_id", nullable = false, unique = true, length = 11)
    private String youtubeId;

    private String title;
    private String description;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private String channelId;
    private String channelName;
    private Instant publishedAt;
    private LocalDate videoDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_amendments", schema = "videos")
    @Enumerated(EnumType.STRING)
    private Set<Amendment> amendments;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_participants", schema = "videos")
    @Enumerated(EnumType.STRING)
    private Set<Participant> participants;

    @Enumerated(EnumType.STRING)
    private VideoStatus status;

    private UUID submittedBy;

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL)
    private List<VideoLocation> locations;

    private Instant createdAt;
    private String sysPeriod;  // Read-only
}
```

### VideoLocation Entity

```java
@Entity
@Table(name = "video_locations", schema = "videos")
public class VideoLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    private UUID locationId;
    private boolean primary;

    // Cached location data
    private String displayName;
    private String city;
    private String state;
    private Double latitude;
    private Double longitude;

    private Instant createdAt;
    private String sysPeriod;  // Read-only
}
```

---

## Temporal vs Non-Temporal Decisions

| Table | Temporal | Rationale |
|-------|----------|-----------|
| `videos` | Yes | Audit trail for video edits, status changes |
| `video_amendments` | No | Element collection, recreated on update |
| `video_participants` | No | Element collection, recreated on update |
| `video_locations` | Yes | Track when locations added/removed |

---

## Index Strategy

| Index | Column(s) | Purpose |
|-------|-----------|---------|
| `idx_videos_youtube_id` | `youtube_id` | Duplicate detection |
| `idx_videos_status` | `status` | Filter by approval status |
| `idx_videos_submitted_by` | `submitted_by` | User's submissions |
| `idx_videos_created_at` | `created_at` | Sort by creation time |
| `idx_video_locations_video_id` | `video_id` | Join queries |
| `idx_video_locations_location_id` | `location_id` | Reverse lookup |

---

## Common Query Patterns

### Check if YouTube video already submitted

```java
boolean existsByYoutubeId(String youtubeId);
```

### List approved videos

```java
Page<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);
```

### Get user's submissions

```java
Page<Video> findBySubmittedBy(UUID submittedBy, Pageable pageable);
```

### Check video-location exists

```java
boolean existsByVideoIdAndLocationId(UUID videoId, UUID locationId);
```
