# AcctAtlas Video Service

Core content management service for AccountabilityAtlas. Manages video records, YouTube API integration, amendment categorization, and video-location associations.

## Prerequisites

- **Docker Desktop** (for PostgreSQL + Redis)
- **Git**
- **YouTube Data API Key** (for metadata fetching)

JDK 21 is managed automatically by the Gradle wrapper via [Foojay Toolchain](https://github.com/gradle/foojay-toolchain) -- no manual JDK installation required.

## Clone and Build

```bash
git clone <repo-url>
cd AcctAtlas-video-service
```

Build the project (downloads JDK 21 automatically on first run):

```bash
# Linux/macOS
./gradlew build

# Windows
gradlew.bat build
```

## Local Development

### Start dependencies

```bash
docker-compose up -d
```

This starts PostgreSQL 17 and Redis 7. Flyway migrations run automatically when the service starts.

### Set environment variables

```bash
# Required for YouTube metadata fetching
export YOUTUBE_API_KEY=your_key_here
```

### Run the service

```bash
# Linux/macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

The service starts on **http://localhost:8082**.

### Quick API test

```bash
# Health check
curl http://localhost:8082/actuator/health

# Submit a video (requires auth token from user-service)
curl -X POST http://localhost:8082/videos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"youtubeUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","amendments":["FIRST","FOURTH"],"participants":["POLICE","CITIZEN"]}'

# List approved videos
curl http://localhost:8082/videos

# Get video by ID (replace ID with actual UUID)
curl http://localhost:8082/videos/550e8400-e29b-41d4-a716-446655440000
```

### Run tests

```bash
./gradlew test
```

Integration tests use [TestContainers](https://testcontainers.com/) to spin up PostgreSQL automatically -- Docker must be running.

### Code formatting

Formatting is enforced by [Spotless](https://github.com/diffplug/spotless) using Google Java Format.

```bash
# Check formatting
./gradlew spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply
```

### Full quality check

Runs Spotless, Error Prone, tests, and JaCoCo coverage verification (80% minimum):

```bash
./gradlew check
```

## Docker Image

Build a Docker image locally using [Jib](https://github.com/GoogleContainerTools/jib) (no Dockerfile needed):

```bash
./gradlew jibDockerBuild
```

Build and start the full stack (service + Postgres + Redis) in Docker:

```bash
./gradlew composeUp
```

## Project Structure

```
src/main/java/com/accountabilityatlas/videoservice/
  config/        Spring configuration (Security, YouTube, WebClient)
  domain/        JPA entities (Video, VideoLocation, enums)
  repository/    Spring Data JPA repositories
  service/       Business logic (VideoService, YouTubeService, LocationClient)
  web/           Controller implementations
  exception/     Custom exceptions and global handler

src/main/resources/
  application.yml          Shared config
  application-local.yml    Local dev overrides
  db/migration/            Flyway SQL migrations

src/test/java/...
  service/       Service unit tests (Mockito)
  web/           Controller tests (@WebMvcTest)
  integration/   Integration tests (TestContainers)
```

API interfaces and DTOs are generated from `docs/api-specification.yaml` by the OpenAPI Generator plugin into `build/generated/`.

## Key Gradle Tasks

| Task | Description |
|------|-------------|
| `bootRun` | Run the service locally (uses `local` profile) |
| `test` | Run all tests |
| `unitTest` | Run unit tests only (no Docker required) |
| `integrationTest` | Run integration tests only (requires Docker) |
| `check` | Full quality gate (format + analysis + tests + coverage) |
| `spotlessApply` | Auto-fix code formatting |
| `jibDockerBuild` | Build Docker image |
| `composeUp` | Build image + docker-compose up |
| `composeDown` | Stop docker-compose services |

## Documentation

- [Technical Overview](docs/technical.md)
- [API Specification](docs/api-specification.yaml) (OpenAPI 3.1)
- [Database Schema](docs/database-schema.md)
