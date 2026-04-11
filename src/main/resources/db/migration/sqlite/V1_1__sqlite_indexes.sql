CREATE UNIQUE INDEX uq_media_items_external_ids ON media_items (
    COALESCE(imdb_id, ''),
    COALESCE(tmdb_id, ''),
    COALESCE(tvdb_id, '')
);

CREATE UNIQUE INDEX uq_users_jellyfin_user_id ON users (jellyfin_user_id);

CREATE UNIQUE INDEX uq_seasons_media_item_season ON seasons (media_item_id, season_number);

CREATE INDEX idx_play_events_media_item ON play_events (media_item_id);
CREATE INDEX idx_play_events_user ON play_events (user_id);
CREATE INDEX idx_play_events_played_at ON play_events (played_at);

CREATE INDEX idx_media_items_imdb ON media_items (imdb_id);
CREATE INDEX idx_media_items_tmdb ON media_items (tmdb_id);
CREATE INDEX idx_media_items_tvdb ON media_items (tvdb_id);
