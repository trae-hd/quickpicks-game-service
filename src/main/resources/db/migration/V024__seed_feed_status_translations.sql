INSERT INTO feed_status_translations (provider_id, provider_status, canonical_status, alert_severity) VALUES
('api-football', 'TBD', 'SCHEDULED', 'NONE'),
('api-football', 'NS',  'SCHEDULED', 'NONE'),
('api-football', '1H',  'LIVE',      'NONE'),
('api-football', 'HT',  'LIVE',      'NONE'),
('api-football', '2H',  'LIVE',      'NONE'),
('api-football', 'ET',  'LIVE',      'NONE'), -- Extra time maps to LIVE
('api-football', 'P',   'LIVE',      'NONE'), -- Penalties maps to LIVE
('api-football', 'FT',  'FINISHED',  'NONE'),
('api-football', 'AET', 'FINISHED',  'NONE'), -- Finished after extra time
('api-football', 'PEN', 'FINISHED',  'NONE'), -- Finished after penalties
('api-football', 'PST', 'POSTPONED', 'NONE'),
('api-football', 'CANC', 'CANCELLED', 'NONE'),
('api-football', 'ABD',  'CANCELLED', 'NONE'),
('api-football', 'AWD',  'FINISHED',  'NONE'),
('api-football', 'WO',   'FINISHED',  'NONE'),
('api-football', 'SUSP', 'LIVE',      'HIGH'), -- Suspended triggers alert
('api-football', 'INT',  'LIVE',      'HIGH'), -- Interrupted triggers alert
('api-football', 'LIVE', 'LIVE',      'NONE');
