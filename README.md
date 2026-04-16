# Janitorr Stats - Jellyfin Viewing History Service

> A lightweight companion microservice for [Janitorr](https://github.com/Schaka/janitorr) that archives Jellyfin viewing history against stable external IDs.

---

## Why This Exists

Jellyfin stats applications like Jellystat and Streamystats store and query viewing history using Jellyfin's internal item IDs. Those IDs are not stable - they change when a library is rescanned, when media files are moved, or when a server is migrated. The result is broken history lookups and missing data.

This service solves that by:

- Polling Jellyfin continuously and immediately resolving each item to its IMDB, TMDB, and/or TVDB ID
- Storing all history keyed on those external IDs only
- Exposing a simple query API so Janitorr and similar tools can look up history without caring about Jellyfin internals

It is intentionally minimal. There is no UI, no statistics aggregation, no dashboards. It is a microservice that stores and serves viewing history. Nothing else.

---

## Tech Stack

- **Kotlin** - primary language
- **Quarkus** - framework; chosen for its mature GraalVM native image pipeline and multi-arch Docker support
- **Hibernate + Panache** - persistence, repository pattern
- **Flyway** - database migrations, per-vendor SQL paths
- **PostgreSQL** - production database
- **SQLite** - lightweight alternative for NAS, SBC, or single-user deployments
- **GraalVM / Mandrel** - native compilation to `linux/amd64` and `linux/arm64`

---

## Deployment

The service is distributed as a multi-arch Docker image supporting `linux/amd64` and `linux/arm64`. No JVM is required at runtime - the image contains a natively compiled binary.

Configuration is supplied by mounting a YAML file into the container at `/work/config/application.yaml`. The repo provides two ready-to-use templates — pick the one matching your database, fill in your values, and mount it.

### Docker Compose (PostgreSQL)

Copy [`config-postgresql.yaml`](./config-postgresql.yaml), fill in your Jellyfin URL, API key, and database credentials, then:

```yaml
services:
  janitorr-stats:
    container_name: janitorr-stats
    image: ghcr.io/schaka/janitorr-stats:stable
    user: 1000:1000
    environment:
      TZ: Europe/Berlin
    volumes:
      - /appdata/janitorr-stats/application.yaml:/work/config/application.yaml
      - /appdata/janitorr-stats/data:/data
      - /appdata/janitorr-stats/logs:/logs # optional - only needed if quarkus.log.file.enable is true
    ports:
      - "8080:8080" # be careful about publishing, this server has no auth and is best kept only accessible to other containers
    depends_on:
      - db

  db:
    image: postgres:18
    environment:
      POSTGRES_DB: janitorr_stats
      POSTGRES_USER: janitorr_stats
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

### Docker Compose (SQLite)

Copy [`config-sqlite.yaml`](./config-sqlite.yaml), fill in your Jellyfin URL and API key, and adjust the database path if needed, then:

```yaml
services:
  janitorr-stats:
    container_name: janitorr-stats
    image: ghcr.io/schaka/janitorr-stats:stable-sqlite
    user: 1000:1000
    environment:
      TZ: Europe/Berlin
    volumes:
      - /appdata/janitorr-stats/application.yaml:/work/config/application.yaml
      - /appdata/janitorr-stats/data:/data
      - /appdata/janitorr-stats/logs:/logs # optional - only needed if quarkus.log.file.enable is true
    ports:
      - "8080:8080" # be careful about publishing, this server has no auth and is best kept only accessible to other containers
```

The `/data` volume must match the path used in `quarkus.datasource.jdbc.url` inside your config file.

---

## Configuration

The config file format follows standard Quarkus YAML. Only the properties you include override the bundled defaults.

| Property | Default | Notes |
|---|---|---|
| `jellyfin.base-url` | — | Required |
| `jellyfin.api-key` | — | Required |
| `jellyfin.poll-interval` | `60s` | Quarkus duration syntax: `30s`, `2m`, etc. |
| `quarkus.datasource.db-kind` | `postgresql` | `postgresql` or `sqlite` |
| `quarkus.datasource.jdbc.url` | — | Required |
| `quarkus.datasource.username` | — | PostgreSQL only |
| `quarkus.datasource.password` | — | PostgreSQL only |
| `quarkus.log.category."com.github.schaka".level` | `DEBUG` | `DEBUG`, `INFO`, or `WARN` |
| `quarkus.log.file.enabled` | `false` | Set to `true` to enable file logging |
| `quarkus.log.file.path` | — | Path to the log file inside the container (e.g. `/logs/janitorr-stats.log`) |

All properties can alternatively be set as environment variables using Quarkus's standard naming convention (e.g. `jellyfin.base-url` → `JELLYFIN_BASE_URL`). Environment variables take precedence over the mounted config file.

---

## API

The OpenAPI spec is published with each release and always available at:

```
https://github.com/Schaka/janitorr-stats/releases/latest/download/openapi.yaml
```

The service exposes two endpoints. Both return paginated responses.

### `GET /history/movies`

Returns a page of play history for a movie. At least one ID parameter is required.

**Parameters:**

| Name | Type | Default | Description |
|---|---|---|---|
| `imdbId` | string | — | IMDB ID, e.g. `tt1234567` |
| `tmdbId` | string | — | TMDB movie ID |
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `100` | Number of results per page |

**Example:**

```
GET /history/movies?imdbId=tt1234567
GET /history/movies?tmdbId=680&page=1&size=50
```

**Response:**

```json
{
  "content": [
    {
      "userId": "...",
      "username": "alice",
      "playedAt": "2025-11-03T21:14:00Z",
      "percentComplete": 97,
      "completed": true,
      "durationMs": 7234000,
      "positionMs": 7018000
    }
  ],
  "page": 0,
  "pageSize": 100,
  "totalItems": 1,
  "totalPages": 1
}
```

---

### `GET /history/shows`

Returns a page of play history for a TV series, optionally filtered to a specific season or episode. At least one series ID parameter is required.

**Parameters:**

| Name | Type | Default | Description |
|---|---|---|---|
| `imdbId` | string | — | IMDB ID of the series |
| `tmdbId` | string | — | TMDB series ID |
| `tvdbId` | string | — | TVDB series ID |
| `season` | integer | — | Optional - filter by season number |
| `episode` | integer | — | Optional - filter by episode number within the season |
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `100` | Number of results per page |

**Example:**

```
GET /history/shows?tvdbId=121361
GET /history/shows?tvdbId=121361&season=3
GET /history/shows?tvdbId=121361&season=3&episode=9&page=0&size=25
```

**Response:**

```json
{
  "content": [
    {
      "userId": "...",
      "username": "alice",
      "seasonNumber": 3,
      "episodeNumber": 9,
      "playedAt": "2025-11-04T20:00:00Z",
      "percentComplete": 100,
      "completed": true,
      "durationMs": 3421000,
      "positionMs": 3421000
    }
  ],
  "page": 0,
  "pageSize": 25,
  "totalItems": 1,
  "totalPages": 1
}
```

When multiple IDs are provided, they are ANDed - all must match the same series record. This prevents false positives from ID collisions across external databases.

---

## Running Locally

**Prerequisites:** Java 25, Docker (used by Testcontainers to provision local services)

```bash
./gradlew quarkusDev
```

That single command is all that is needed. Quarkus Dev Services handle the rest automatically:

- A **PostgreSQL** container is started and wired to the datasource configuration
- A **Jellyfin** container is started, its setup wizard is completed, an API key is generated, and that key is injected into the application config at runtime - no manual Jellyfin setup is required
- A local media library is prepared under `local-runtime/media/` with a set of DRM-free sample video files, organised into movies and TV show seasons; Jellyfin is pointed at this directory and a library scan is triggered automatically

Once the application is up, it will begin polling the local Jellyfin instance on the configured interval and recording play events as they occur. The Quarkus Dev UI is available at `http://localhost:8080/q/dev/` and includes live reload.

When the application shuts down, the Jellyfin container is stopped. The local runtime directory persists between runs, so the library and Jellyfin configuration are reused and the setup wizard is skipped on subsequent starts.

---

## Relationship to Janitorr

This service is a companion to Janitorr, not a replacement for existing stats apps. Janitorr will fall back to this service when Jellystat or Streamystats returns no result for a given item, and will allow users to migrate fully once they are comfortable with the coverage.

Neither application depends on the other to run. This service has no knowledge of Janitorr.

---

## Contributing

- Kotlin only - no Java source files
- No inline comments unless explaining a non-obvious decision
- KDoc on all public classes and methods
- No Jellyfin internal IDs in any response model or query path
- All timestamps in UTC
- Migrations in plain SQL under `src/main/resources/db/migration/`
