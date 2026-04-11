# CLAUDE.md

This file captures the architectural decisions, conventions, and context for this project. It is intended to guide AI-assisted development and onboard contributors quickly.

## What This Service Does

This is a lightweight companion microservice to [Janitorr](https://github.com/Schaka/janitorr). It solves a specific problem with existing Jellyfin stats applications (Jellystat, Streamystats): those tools tie their data model to Jellyfin's internal IDs, which are unstable across library rescans, server migrations, and re-imports.

This service:
- Continuously polls a Jellyfin instance for play activity via a scheduled job
- Stores viewing history keyed entirely on external IDs (IMDB, TMDB, TVDB) — never on Jellyfin's internal IDs
- Exposes a minimal REST API to query play history by external ID, intended to be consumed by Janitorr or similar tools
- Does not provide a UI, dashboards, or user-facing stats — it is a data microservice only

The canonical GitHub issue describing the motivation is [Janitorr #240](https://github.com/Schaka/janitorr/issues/240).

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Framework | Quarkus |
| ORM | Hibernate with Panache (repository pattern) |
| Migrations | Flyway |
| Production DB | PostgreSQL |
| Lightweight DB | SQLite (via Quarkus JDBC SQLite extension) |
| Native compilation | GraalVM / Mandrel |
| Container targets | `linux/amd64`, `linux/arm64` |

### Why Quarkus

Quarkus was chosen over Micronaut and Spring Boot for the following reasons:

- Native image compilation via GraalVM/Mandrel is a first-class, well-documented workflow in Quarkus, not an afterthought
- The multi-arch Docker story integrates cleanly with CI pipelines that build per-arch and combine into a single manifest
- Dev Services automatically provision a local Postgres or SQLite instance during development without configuration
- The extension ecosystem verifies native-image safety explicitly, which reduces runtime surprises when adding dependencies

### Why SQLite Instead of H2

H2 in Postgres compatibility mode was considered and rejected. The compatibility mode is a shim, not a real implementation. It diverges silently on window functions, `ON CONFLICT` clauses, and other non-trivial SQL, and its behaviour is version-sensitive relative to the Postgres dialect it claims to emulate.

SQLite is a real database engine. Its behaviour is predictable and consistent. It is a single file, easy to back up, and well-suited to low-resource deployments on ARM hardware (NAS devices, SBCs) which are a primary deployment target.

H2 is not used anywhere in this project.

### Why Not a Single Flyway Migration Path

Flyway is configured with per-vendor migration locations:

- `db/migration/common` — ANSI SQL DDL that runs on both backends
- `db/migration/postgres` — Postgres-specific syntax (e.g. index strategies, conflict handling)
- `db/migration/sqlite` — SQLite-specific syntax where needed

The active location set is determined by the configured datasource dialect. Users do not need to manage this manually.

Liquibase was considered but rejected. Its XML/YAML abstraction layer adds ceremony without benefit for a schema this small. Flyway's plain SQL files are easier to read, review, and debug.

## Data Model

The schema is intentionally designed around external IDs. Jellyfin's internal IDs appear only in a dedicated mapping column, used exclusively for debugging and correlation during polling. They are never used as foreign keys or join targets.

### Core Tables

**`media_items`** — canonical record per piece of media

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | Internal primary key |
| `media_type` | ENUM | `MOVIE` or `SERIES` |
| `title` | VARCHAR | Display title |
| `year` | INT | Release year, used as disambiguation aid |
| `imdb_id` | VARCHAR | `tt`-prefixed, nullable |
| `tmdb_id` | VARCHAR | Nullable |
| `tvdb_id` | VARCHAR | Nullable |
| `jellyfin_item_id` | VARCHAR | Debug/correlation only, not used in queries |

At least one of `imdb_id`, `tmdb_id`, or `tvdb_id` must be non-null. The uniqueness constraint is across the combination of external IDs.

**`seasons`** — one row per season of a series

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `media_item_id` | UUID FK → `media_items` | |
| `season_number` | INT | |
| `tmdb_season_id` | VARCHAR | Nullable |
| `tvdb_season_id` | VARCHAR | Nullable |
| `jellyfin_season_id` | VARCHAR | Debug only |

**`users`** — Jellyfin users observed in play events

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `jellyfin_user_id` | VARCHAR | Unique, the only place Jellyfin user IDs are stored |
| `username` | VARCHAR | Snapshot at time of last event |

**`play_events`** — one row per discrete play session

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → `users` | |
| `media_item_id` | UUID FK → `media_items` | |
| `season_number` | INT | Null for movies |
| `episode_number` | INT | Null for movies |
| `played_at` | TIMESTAMP | UTC |
| `duration_ms` | BIGINT | Total runtime of the item |
| `position_ms` | BIGINT | Position at which session ended |
| `percent_complete` | INT | Derived at write time, stored for query convenience |
| `completed` | BOOLEAN | True if `percent_complete >= 90` |

## Jellyfin Polling Strategy

Polling is performed via Quarkus's `@Scheduled` mechanism on a configurable interval (default: 60 seconds).

On each tick, the scheduler:

1. Calls `/Sessions` to retrieve active and recently-ended sessions
2. For each session referencing a media item, calls `/Items/{jellyfinItemId}` to resolve external IDs (IMDB, TMDB, TVDB)
3. Upserts the resolved `media_item` record using external IDs as the natural key — Jellyfin's item ID is stored alongside but never used as the lookup key
4. Writes a `play_event` if the session represents new or updated play activity not already recorded

On startup, the scheduler performs a backfill pass against Jellyfin's activity log endpoint to catch events that occurred while the service was offline.

The Jellyfin item ID is treated as a transient lookup handle during the polling pass only. It is stored for debugging purposes but plays no role in the data model's integrity or query paths.

## API Design

Two endpoints. No more.

### `GET /history/movies`

Query viewing history for movies.

**Query parameters (at least one required):**

| Parameter | Description |
|---|---|
| `imdbId` | IMDB ID (e.g. `tt1234567`) |
| `tmdbId` | TMDB movie ID |

**Response:** list of `PlayEventResponse` objects ordered by `playedAt` descending.

### `GET /history/shows`

Query viewing history for TV episodes.

**Query parameters (at least one required, plus optional season/episode filter):**

| Parameter | Description |
|---|---|
| `imdbId` | IMDB ID of the series |
| `tmdbId` | TMDB series ID |
| `tvdbId` | TVDB series ID |
| `season` | Optional — filter to a specific season number |
| `episode` | Optional — filter to a specific episode number within the season |

**Response:** list of `PlayEventResponse` objects.

### Lookup resolution order

When multiple IDs are provided, they are ANDed — all supplied IDs must match the same `media_items` record. This prevents false positives from ID collisions across databases.

## Code Conventions

- No inline comments unless the reason behind a decision is not obvious from the code itself
- Javadoc / KDoc on all public methods and classes
- No comments restating what the code does — only why, when that is not self-evident
- Repository pattern via Panache — entities are not returned directly from repositories; mapped response types are used at the API boundary
- No Jellyfin internal IDs in any query path or response model
- All timestamps stored and returned as UTC
- Kotlin data classes for request/response models; Panache entities for persistence

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `jellyfin.base-url` | — | Base URL of the Jellyfin instance |
| `jellyfin.api-key` | — | API key with read access |
| `jellyfin.poll-interval` | `60s` | How often to poll `/Sessions` |
| `quarkus.datasource.db-kind` | `postgresql` | Set to `sqlite` for lightweight deployments |
| `quarkus.datasource.jdbc.url` | — | JDBC URL for the chosen database |
| `quarkus.flyway.locations` | resolved by dialect | Do not override unless you know what you are doing |
