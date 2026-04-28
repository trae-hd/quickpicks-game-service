INSERT INTO feed_field_mappings (provider_id, canonical_field, provider_json_path) VALUES
('api-football', 'match_id', '$.fixture.id'),
('api-football', 'home_team', '$.teams.home.name'),
('api-football', 'away_team', '$.teams.away.name'),
('api-football', 'kick_off', '$.fixture.date'),
('api-football', 'status', '$.fixture.status.short'),
('api-football', 'home_score', '$.score.fulltime.home'),
('api-football', 'away_score', '$.score.fulltime.away'),
('api-football', 'league_id', '$.league.id'),
('api-football', 'league_name', '$.league.name'),
('api-football', 'venue_name', '$.fixture.venue.name'),
('api-football', 'elapsed_time', '$.fixture.status.elapsed'),
('api-football', 'halftime_home_score', '$.score.halftime.home'),
('api-football', 'halftime_away_score', '$.score.halftime.away');
