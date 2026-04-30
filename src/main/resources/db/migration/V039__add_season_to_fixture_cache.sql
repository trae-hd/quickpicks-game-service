ALTER TABLE fixture_cache ADD COLUMN season TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_fixture_cache_season ON fixture_cache(season);
CREATE INDEX idx_fixture_cache_season_league ON fixture_cache(season, league_name);