-- Seed api-football league IDs. Verify these against your api-football subscription
-- at https://www.api-football.com/documentation-v3#tag/Leagues before deploying.

INSERT INTO feed_league_mappings (provider_id, provider_league_id, league_name, country) VALUES
    -- UEFA Competitions
    ('api-football', '2',   'UEFA Champions League',      'Europe'),
    ('api-football', '3',   'UEFA Europa League',         'Europe'),
    ('api-football', '848', 'UEFA Europa Conference League', 'Europe'),

    -- England
    ('api-football', '39',  'Premier League',             'England'),
    ('api-football', '40',  'Championship',               'England'),
    ('api-football', '41',  'League One',                 'England'),
    ('api-football', '42',  'League Two',                 'England'),
    ('api-football', '45',  'FA Cup',                     'England'),
    ('api-football', '48',  'EFL Cup',                    'England'),

    -- Scotland
    ('api-football', '179', 'Scottish Premiership',       'Scotland'),

    -- Spain
    ('api-football', '140', 'La Liga',                    'Spain'),
    ('api-football', '141', 'La Liga 2',                  'Spain'),

    -- Germany
    ('api-football', '78',  'Bundesliga',                 'Germany'),
    ('api-football', '79',  '2. Bundesliga',              'Germany'),

    -- Italy
    ('api-football', '135', 'Serie A',                    'Italy'),
    ('api-football', '136', 'Serie B',                    'Italy'),

    -- France
    ('api-football', '61',  'Ligue 1',                    'France'),
    ('api-football', '62',  'Ligue 2',                    'France'),

    -- Netherlands
    ('api-football', '88',  'Eredivisie',                 'Netherlands'),

    -- Portugal
    ('api-football', '94',  'Primeira Liga',              'Portugal'),

    -- Belgium
    ('api-football', '144', 'Belgian Pro League',         'Belgium'),

    -- Turkey
    ('api-football', '203', 'Süper Lig',                  'Turkey');