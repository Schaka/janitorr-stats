# Navidrome Stats — Jellyfin Viewing History Service

> A lightweight companion microservice for [Janitorr](https://github.com/Schaka/janitorr) that archives Jellyfin viewing history against stable external IDs.

---

## Why This Exists

Jellyfin stats applications like Jellystat and Streamystats store and query viewing history using Jellyfin's internal item IDs. Those IDs are not stable — they change when a library is rescanned, when media files are moved, or when a server is migrated. The result is broken history lookups and missing data.

This service solves that by:

- Polling Jellyfin continuously and immediately resolving each item to its IMDB, TMDB, and/or TVDB ID
- Storing all history keyed on those external IDs only
- Exposing a simple query API so Janitorr and similar tools can look up history without caring about Jellyfin internals

It is intentionally minimal. There is no UI, no statistics aggregation, no dashboards. It is a microservice that stores and serves viewing history. Nothing else.

---

## Tech Stack

- **Kotlin** — primary language
- **Quarkus** — framework; chosen for its mature GraalVM native image pipeline and multi-arch Docker support
- **Hibernate + Panache** — persistence, repository pattern
- **Flyway** — database migrations, per-vendor SQL paths
- **PostgreSQL** — production database
- **SQLite** — lightweight alternative for NAS, SBC, or single-user deployments
- **GraalVM / Mandrel** — native compilation to `linux/amd64` and `linux/arm64`

---

## Deployment

The service is distributed as a multi-arch Docker image supporting `linux/amd64` and `linux/arm64`. No JVM is required at runtime — the image contains a natively compiled binary.

### Docker Compose (PostgreSQL)

```yaml
services:
  jfhist:
    image: ghcr.io/schaka/jfhist:latest
    environment:
      JELLYFIN_BASE_URL: http://your-jellyfin:8096
      JELLYFIN_API_KEY: your_api_key_here
      QUARKUS_DATASOURCE_DB_KIND: postgresql
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://db:5432/jfhist
      QUARKUS_DATASOURCE_USERNAME: jfhist
      QUARKUS_DATASOURCE_PASSWORD: secret
    ports:
      - "8080:8080"
    depends_on:
      - db

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: jfhist
      POSTGRES_USER: jfhist
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

### Docker Compose (SQLite)

```yaml
services:
  jfhist:
    image: ghcr.io/schaka/jfhist:latest
    environment:
      JELLYFIN_BASE_URL: http://your-jellyfin:8096
      JELLYFIN_API_KEY: your_api_key_here
      QUARKUS_DATASOURCE_DB_KIND: sqlite
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:sqlite:/data/jfhist.db
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
```

---

## Configuration

| Environment Variable | Default | Required | Description |
|---|---|---|---|
| `JELLYFIN_BASE_URL` | — | Yes | Base URL of your Jellyfin instance |
| `JELLYFIN_API_KEY` | — | Yes | API key with read access |
| `JELLYFIN_POLL_INTERVAL` | `60s` | No | Polling interval for active sessions |
| `QUARKUS_DATASOURCE_DB_KIND` | `postgresql` | No | `postgresql` or `sqlite` |
| `QUARKUS_DATASOURCE_JDBC_URL` | — | Yes | Full JDBC URL for chosen database |
| `QUARKUS_DATASOURCE_USERNAME` | — | PostgreSQL only | |
| `QUARKUS_DATASOURCE_PASSWORD` | — | PostgreSQL only | |

---

## API

The service exposes two endpoints.

### `GET /history/movies`

Returns play history for a movie. At least one ID parameter is required.

**Parameters:**

| Name | Type | Description |
|---|---|---|
| `imdbId` | string | IMDB ID, e.g. `tt1234567` |
| `tmdbId` | string | TMDB movie ID |

**Example:**

```
GET /history/movies?imdbId=tt1234567
GET /history/movies?tmdbId=680
```

**Response:**

```json
[
  {
    "userId": "...",
    "username": "alice",
    "playedAt": "2025-11-03T21:14:00Z",
    "percentComplete": 97,
    "completed": true,
    "durationMs": 7234000,
    "positionMs": 7018000
  }
]
```

---

### `GET /history/shows`

Returns play history for a TV series, optionally filtered to a specific season or episode. At least one series ID parameter is required.

**Parameters:**

| Name | Type | Description |
|---|---|---|
| `imdbId` | string | IMDB ID of the series |
| `tmdbId` | string | TMDB series ID |
| `tvdbId` | string | TVDB series ID |
| `season` | integer | Optional — filter by season number |
| `episode` | integer | Optional — filter by episode number within the season |

**Example:**

```
GET /history/shows?tvdbId=121361
GET /history/shows?tvdbId=121361&season=3
GET /history/shows?tvdbId=121361&season=3&episode=9
```

**Response:**

```json
[
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
]
```

When multiple IDs are provided, they are ANDed — all must match the same series record. This prevents false positives from ID collisions across external databases.

---

## How Polling Works

On a configurable schedule (default: every 60 seconds), the service:

1. Calls Jellyfin's `/Sessions` endpoint to retrieve active and recently-ended play sessions
2. For each session, calls `/Items/{id}` to resolve the Jellyfin item ID to external IDs (IMDB, TMDB, TVDB)
3. Upserts a `media_items` record using external IDs as the natural key — the Jellyfin ID is stored alongside it for debugging only
4. Records a `play_event` if the session represents new or changed activity

On startup, the service also runs a backfill pass against Jellyfin's activity log to recover events that occurred while it was offline.

Jellyfin's internal item IDs are used only as transient lookup handles during the poll pass. They are never used as keys, foreign keys, or in any query path.

---

## Database Design

All history is stored against external IDs. Jellyfin's internal IDs appear only in dedicated columns marked for debug use — they have no role in joins, lookups, or API responses.

```
media_items
  id (PK)
  media_type         MOVIE | SERIES
  title
  year
  imdb_id            nullable
  tmdb_id            nullable
  tvdb_id            nullable
  jellyfin_item_id   debug/correlation only

seasons
  id (PK)
  media_item_id (FK)
  season_number
  tmdb_season_id     nullable
  tvdb_season_id     nullable
  jellyfin_season_id debug only

users
  id (PK)
  jellyfin_user_id   only place Jellyfin user IDs live
  username

play_events
  id (PK)
  user_id (FK)
  media_item_id (FK)
  season_number      null for movies
  episode_number     null for movies
  played_at          UTC
  duration_ms
  position_ms
  percent_complete
  completed
```

---

## Relationship to Janitorr

This service is a companion to Janitorr, not a replacement for existing stats apps. Janitorr will fall back to this service when Jellystat or Streamystats returns no result for a given item, and will allow users to migrate fully once they are comfortable with the coverage.

Neither application depends on the other to run. This service has no knowledge of Janitorr.

---

## Building from Source

### JVM mode (development)

```bash
./gradlew quarkusDev
```

Dev Services will automatically start a local database. No additional setup is needed.

### Native image

```bash
./gradlew build -Dquarkus.package.type=native
```

Requires GraalVM or Mandrel to be installed and on `PATH`.

### Multi-arch Docker image

The CI pipeline builds separate images for `linux/amd64` and `linux/arm64` and combines them into a single manifest. To build locally for a single arch:

```bash
docker build -f src/main/docker/Dockerfile.native -t jfhist .
```

---

## Contributing

- Kotlin only — no Java source files
- No inline comments unless explaining a non-obvious decision
- KDoc on all public classes and methods
- No Jellyfin internal IDs in any response model or query path
- All timestamps in UTC
- Migrations in plain SQL under `src/main/resources/db/migration/`
