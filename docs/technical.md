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
| Framework | Spring Boot 3.4.x |
| Language | Java 21 |
| Build | Gradle |
| Database | PostgreSQL 15 |
| HTTP Client | WebClient (reactive) |

## Dependencies

- **PostgreSQL**: Video records, video-location associations
- **YouTube Data API**: Video metadata, thumbnail URLs, validation
- **location-service**: Location validation (REST)
- **user-service**: JWT validation via JWKS endpoint
- **SQS**: Event publishing

## JWT Authentication

The video-service validates JWTs issued by user-service using Spring's OAuth2 Resource Server. It fetches the RSA public key from user-service's JWKS endpoint to verify token signatures.

### Configuration

```yaml
# application-local.yml / application-docker.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://user-service:8081/.well-known/jwks.json
```

### Behavior

- **GET endpoints** (list/view videos): Accessible without authentication. If a Bearer token is present, it is decoded to provide user context (e.g., for visibility rules).
- **POST/PUT/DELETE endpoints**: Require a valid JWT. The controller reads `sub` (user ID) and `trustTier` from the token claims via `SecurityContextHolder`.
- **Invalid/expired tokens**: Return 401 Unauthorized.
- **Internal endpoints** (`/internal/**`): No JWT validation — access controlled by IP/CIDR allowlist.

## Documentation Index

| Document | Status | Description |
|----------|--------|-------------|
| [api-specification.yaml](api-specification.yaml) | Complete | OpenAPI 3.1 specification |
| [database-schema.md](database-schema.md) | Planned | Schema documentation |
| [youtube-integration.md](youtube-integration.md) | Planned | YouTube API integration details |
| [amendment-taxonomy.md](amendment-taxonomy.md) | Planned | Amendment categorization guide |

## Domain Model

```
Video (temporal - sys_period tracks history)
├── id: UUID
├── youtubeId: String
├── title: String
├── description: String (nullable)
├── thumbnailUrl: String
├── durationSeconds: Integer (nullable)
├── channelId: String (nullable)        // YouTube channel ID
├── channelName: String (nullable)      // YouTube channel name
├── publishedAt: Instant (nullable)     // When published on YouTube
├── videoDate: LocalDate (nullable)     // When incident occurred (user-provided)
├── amendments: Set<Amendment>
├── participants: Set<Participant>
├── status: VideoStatus (PENDING, APPROVED, REJECTED, DELETED)
├── submittedBy: UUID
└── sysPeriod: tstzrange  // lower bound = created, NULL upper = current

VideoLocation (temporal - sys_period tracks history)
├── id: UUID
├── videoId: UUID
├── locationId: UUID
├── isPrimary: boolean
└── sysPeriod: tstzrange  // lower bound = added time

enum Amendment { FIRST, SECOND, FOURTH, FIFTH }
enum Participant { POLICE, GOVERNMENT, BUSINESS, CITIZEN }
```

## API Endpoints

### Public Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /videos | Public | List videos (paginated, filtered) |
| GET | /videos/{id} | Public* | Get video details (*visibility rules apply) |
| POST | /videos | User | Submit new video |
| PUT | /videos/{id} | Owner | Update video metadata (non-approved only) |
| DELETE | /videos/{id} | Owner | Delete video (non-approved only) |
| GET | /videos/{id}/locations | Public | Get video locations |
| POST | /videos/{id}/locations | Owner | Add location (non-approved only) |
| DELETE | /videos/{id}/locations/{locId} | Owner | Remove location (non-approved only) |
| GET | /videos/user/{userId} | Public | Get user's submissions |

**Note:** Moderators and admins use moderation-service endpoints to modify videos.

### Internal Endpoints (service-to-service)

Used by moderation-service for video tweaks during review. Not exposed via API Gateway.

| Method | Path | Caller | Description |
|--------|------|--------|-------------|
| PUT | /internal/videos/{id} | moderation-service | Update metadata (bypasses owner check) |
| PUT | /internal/videos/{id}/status | moderation-service | Set APPROVED/REJECTED |
| POST | /internal/videos/{id}/locations | moderation-service | Add location |
| DELETE | /internal/videos/{id}/locations/{locId} | moderation-service | Remove location |

Access controlled by IP/CIDR allowlist (internal network only).

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
| VideoUpdated | Metadata change | search-service |
| VideoDeleted | Video removal | search-service, location-service |

## Events Consumed

| Event | Source | Action |
|-------|--------|--------|
| VideoApproved | moderation-service | Update video status to APPROVED |
| VideoRejected | moderation-service | Update video status to REJECTED |

## YouTube Integration

```java
// Supported URL formats
private static final List<Pattern> YOUTUBE_PATTERNS = List.of(
    Pattern.compile("youtube\\.com/watch\\?v=([\\w-]{11})"),
    Pattern.compile("youtu\\.be/([\\w-]{11})"),
    Pattern.compile("youtube\\.com/embed/([\\w-]{11})")
);

// Fetched metadata from YouTube Data API
public record YouTubeMetadata(
    String videoId,
    String title,
    String description,
    String thumbnailUrl,
    Duration duration,
    String channelId,
    String channelName,
    Instant publishedAt
) { }
```

### Integration Patterns

The YouTube client implements the external API patterns defined in Technical Requirements:

- **Circuit Breaker**: Resilience4j circuit breaker with 5-call sliding window
- **Caching**: 24-hour cache on metadata (Redis) to reduce API quota usage
- **Rate Limiting**: Client-side rate limiter to stay within 10,000 units/day quota
- **Retry**: Exponential backoff (1s, 2s, 4s) for transient failures
- **Timeout**: 5-second timeout per API call

### Error Handling

| YouTube API Error | Internal Handling |
|-------------------|-------------------|
| 404 Not Found | Reject submission with "Video not found" |
| 403 Forbidden | Reject with "Video is private or restricted" |
| 429 Rate Limited | Retry with backoff, alert if persistent |
| 5xx Server Error | Retry with backoff, fallback to cached data |

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
