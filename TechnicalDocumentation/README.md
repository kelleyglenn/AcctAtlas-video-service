# Video Service - Technical Documentation

## Service Overview

The Video Service is the core content management service for AccountabilityAtlas. It manages video records, integrates with YouTube for metadata, handles amendment categorization, and manages video-location associations.

## Responsibilities

- Video record CRUD operations
- YouTube API integration for metadata fetching
- YouTube URL validation
- Amendment categorization (1st, 2nd, 4th, 5th)
- Participant tagging (police, government, business, citizen)
- Video-location associations (multi-location support)
- Ownership tracking and enforcement

## Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.2.x |
| Language | Java 21 |
| Build | Gradle |
| Database | PostgreSQL 15 |
| HTTP Client | WebClient (reactive) |

## Dependencies

- **PostgreSQL**: Video records, video-location associations
- **YouTube Data API**: Video metadata, thumbnail URLs, validation
- **location-service**: Location validation (REST)
- **SQS**: Event publishing

## Documentation Index

| Document | Status | Description |
|----------|--------|-------------|
| [api-specification.yaml](api-specification.yaml) | Planned | OpenAPI 3.0 specification |
| [database-schema.md](database-schema.md) | Planned | Schema documentation |
| [youtube-integration.md](youtube-integration.md) | Planned | YouTube API integration details |
| [amendment-taxonomy.md](amendment-taxonomy.md) | Planned | Amendment categorization guide |

## Domain Model

```
Video
├── id: UUID
├── youtubeId: String
├── title: String
├── description: String (nullable)
├── thumbnailUrl: String
├── durationSeconds: Integer (nullable)
├── videoDate: LocalDate (nullable) // When incident occurred
├── amendments: Set<Amendment>
├── participants: Set<Participant>
├── status: VideoStatus (PENDING, APPROVED, REJECTED, DELETED)
├── submittedBy: UUID
├── createdAt: Instant
└── updatedAt: Instant

VideoLocation
├── id: UUID
├── videoId: UUID
├── locationId: UUID
├── isPrimary: boolean
└── addedAt: Instant

enum Amendment { FIRST, SECOND, FOURTH, FIFTH }
enum Participant { POLICE, GOVERNMENT, BUSINESS, CITIZEN }
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /videos | Public | List videos (paginated, filtered) |
| GET | /videos/{id} | Public | Get video details |
| POST | /videos | User | Submit new video |
| PUT | /videos/{id} | Owner | Update video metadata |
| DELETE | /videos/{id} | Owner/Mod | Delete video |
| GET | /videos/{id}/locations | Public | Get video locations |
| POST | /videos/{id}/locations | Owner | Add location to video |
| DELETE | /videos/{id}/locations/{locId} | Owner | Remove location |
| GET | /videos/user/{userId} | Public | Get user's submissions |

## Query Parameters (GET /videos)

| Parameter | Type | Description |
|-----------|------|-------------|
| status | String | Filter by status (default: APPROVED) |
| amendments | String[] | Filter by amendments |
| participants | String[] | Filter by participants |
| submittedBy | UUID | Filter by submitter |
| page | Int | Page number (0-indexed) |
| size | Int | Page size (default: 20, max: 100) |

## Events Published

| Event | Trigger | Consumers |
|-------|---------|-----------|
| VideoSubmitted | New submission | moderation-service |
| VideoApproved | Moderation approval | search-service, location-service |
| VideoUpdated | Metadata change | search-service |
| VideoDeleted | Video removal | search-service, location-service |

## YouTube Integration

```java
// Supported URL formats
private static final List<Pattern> YOUTUBE_PATTERNS = List.of(
    Pattern.compile("youtube\\.com/watch\\?v=([\\w-]{11})"),
    Pattern.compile("youtu\\.be/([\\w-]{11})"),
    Pattern.compile("youtube\\.com/embed/([\\w-]{11})")
);

// Fetched metadata
public record YouTubeMetadata(
    String title,
    String description,
    String thumbnailUrl,
    Duration duration,
    Instant publishedAt
) { }
```

## Local Development

```bash
# Start dependencies
docker-compose up -d postgres

# Set YouTube API key
export YOUTUBE_API_KEY=your_key_here

# Run migrations
./gradlew flywayMigrate

# Run service
./gradlew bootRun

# Service available at http://localhost:8082
```
