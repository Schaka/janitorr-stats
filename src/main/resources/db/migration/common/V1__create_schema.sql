CREATE TABLE media_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    media_type VARCHAR(10) NOT NULL,
    title VARCHAR(500) NOT NULL,
    year INTEGER,
    imdb_id VARCHAR(20),
    tmdb_id VARCHAR(20),
    tvdb_id VARCHAR(20),
    jellyfin_item_id VARCHAR(100)
);

CREATE TABLE seasons (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    media_item_id VARCHAR(36) NOT NULL,
    season_number INTEGER NOT NULL,
    tmdb_season_id VARCHAR(20),
    tvdb_season_id VARCHAR(20),
    jellyfin_season_id VARCHAR(100),
    CONSTRAINT fk_seasons_media_item FOREIGN KEY (media_item_id) REFERENCES media_items(id)
);

CREATE TABLE users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    jellyfin_user_id VARCHAR(100) NOT NULL,
    username VARCHAR(200) NOT NULL
);

CREATE TABLE play_events (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    media_item_id VARCHAR(36) NOT NULL,
    season_number INTEGER,
    episode_number INTEGER,
    played_at TIMESTAMP NOT NULL,
    duration_ms BIGINT NOT NULL,
    position_ms BIGINT NOT NULL,
    percent_complete INTEGER NOT NULL,
    completed BOOLEAN NOT NULL,
    CONSTRAINT fk_play_events_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_play_events_media_item FOREIGN KEY (media_item_id) REFERENCES media_items(id)
);
