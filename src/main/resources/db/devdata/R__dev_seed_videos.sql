-- Dev seed data: Test videos for local development
-- Real YouTube video IDs from First Amendment audit channels
-- submitted_by references Trusted User from user-service seed data

-- Insert videos (all APPROVED so they appear on map)
INSERT INTO videos.videos (id, youtube_id, title, description, channel_name, status, submitted_by)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'RngL8_3k0C0',
     'Northern California Government Building Audit',
     'First Amendment audit of a government building in Northern California.',
     'Phil', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000002', 'nQRpazbSRf4',
     'East Lansing Police Department Audit Analysis',
     'Audit the Audit analysis of ELPD First Amendment audit incident.',
     'Audit the Audit', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000003', 'ULjtPKeh9Co',
     '61st Precinct Brooklyn - Arrest During Audit',
     'SeanPaul Reyes arrested while recording in the lobby of the 61st Precinct in Brooklyn.',
     'Long Island Audit', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000004', 'AJi0LgnoIJA',
     'Utica Michigan Police Confrontation',
     'Steve Jones confronted by Detective Sergeant during First Amendment audit.',
     'Fricn Media', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000005', 'OdsTAYnC8Kc',
     'Pocahontas City Hall Audit',
     'First Amendment audit at Pocahontas, Arkansas city hall.',
     'The Random Patriot', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000006', '-kNacBPsNxo',
     'San Antonio Strip Mall Encounter',
     'First Amendment audit encounter at a San Antonio strip mall.',
     'Mexican Padilla', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000007', 'IX_8Epjcp54',
     'Leon Valley Police Chief Press Conference',
     'Coverage of Leon Valley Police Department press conference.',
     'News Now Houston', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000008', 'hkhrXPur4ws',
     'Silverthorne Post Office Audit',
     'First Amendment audit at Silverthorne, Colorado post office that led to settlement.',
     'Amagansett Press', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000009', 'QgkT4epLRcw',
     'East Lansing PD Incident',
     'Direct footage from East Lansing Police Department First Amendment audit.',
     'Livingston Audits', 'APPROVED', '00000000-0000-0000-0000-000000000003'),

    ('10000000-0000-0000-0000-000000000010', 'FwvZCn0uLiw',
     'Pocahontas City Hall - Uncut Footage',
     'Full unedited footage from Pocahontas, Arkansas city hall audit.',
     'The Random Patriot', 'APPROVED', '00000000-0000-0000-0000-000000000003')
ON CONFLICT (youtube_id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    channel_name = EXCLUDED.channel_name,
    status = EXCLUDED.status;

-- Insert amendments for each video
INSERT INTO videos.video_amendments (video_id, amendment) VALUES
    -- Video 1: NorCal Audit - FIRST
    ('10000000-0000-0000-0000-000000000001', 'FIRST'),
    -- Video 2: Audit the Audit - FIRST, FOURTH
    ('10000000-0000-0000-0000-000000000002', 'FIRST'),
    ('10000000-0000-0000-0000-000000000002', 'FOURTH'),
    -- Video 3: Brooklyn Arrest - FIRST, FOURTH
    ('10000000-0000-0000-0000-000000000003', 'FIRST'),
    ('10000000-0000-0000-0000-000000000003', 'FOURTH'),
    -- Video 4: Utica MI - FIRST, FOURTH
    ('10000000-0000-0000-0000-000000000004', 'FIRST'),
    ('10000000-0000-0000-0000-000000000004', 'FOURTH'),
    -- Video 5: Pocahontas - FIRST
    ('10000000-0000-0000-0000-000000000005', 'FIRST'),
    -- Video 6: San Antonio - FIRST
    ('10000000-0000-0000-0000-000000000006', 'FIRST'),
    -- Video 7: Leon Valley - FIRST
    ('10000000-0000-0000-0000-000000000007', 'FIRST'),
    -- Video 8: Silverthorne - FIRST
    ('10000000-0000-0000-0000-000000000008', 'FIRST'),
    -- Video 9: East Lansing - FIRST
    ('10000000-0000-0000-0000-000000000009', 'FIRST'),
    -- Video 10: Pocahontas Uncut - FIRST
    ('10000000-0000-0000-0000-000000000010', 'FIRST')
ON CONFLICT (video_id, amendment) DO NOTHING;

-- Insert participants for each video
INSERT INTO videos.video_participants (video_id, participant) VALUES
    -- Video 1: NorCal Audit - POLICE, GOVERNMENT
    ('10000000-0000-0000-0000-000000000001', 'POLICE'),
    ('10000000-0000-0000-0000-000000000001', 'GOVERNMENT'),
    -- Video 2: Audit the Audit - POLICE
    ('10000000-0000-0000-0000-000000000002', 'POLICE'),
    -- Video 3: Brooklyn Arrest - POLICE
    ('10000000-0000-0000-0000-000000000003', 'POLICE'),
    -- Video 4: Utica MI - POLICE, SECURITY
    ('10000000-0000-0000-0000-000000000004', 'POLICE'),
    ('10000000-0000-0000-0000-000000000004', 'SECURITY'),
    -- Video 5: Pocahontas - GOVERNMENT, CITIZEN
    ('10000000-0000-0000-0000-000000000005', 'GOVERNMENT'),
    ('10000000-0000-0000-0000-000000000005', 'CITIZEN'),
    -- Video 6: San Antonio - POLICE, BUSINESS
    ('10000000-0000-0000-0000-000000000006', 'POLICE'),
    ('10000000-0000-0000-0000-000000000006', 'BUSINESS'),
    -- Video 7: Leon Valley - POLICE, GOVERNMENT
    ('10000000-0000-0000-0000-000000000007', 'POLICE'),
    ('10000000-0000-0000-0000-000000000007', 'GOVERNMENT'),
    -- Video 8: Silverthorne - GOVERNMENT
    ('10000000-0000-0000-0000-000000000008', 'GOVERNMENT'),
    -- Video 9: East Lansing - POLICE
    ('10000000-0000-0000-0000-000000000009', 'POLICE'),
    -- Video 10: Pocahontas Uncut - GOVERNMENT
    ('10000000-0000-0000-0000-000000000010', 'GOVERNMENT')
ON CONFLICT (video_id, participant) DO NOTHING;

-- Insert video_locations (denormalized location data)
-- San Francisco Bay Area (Videos 1-5)
INSERT INTO videos.video_locations (id, video_id, location_id, is_primary, display_name, city, state, latitude, longitude) VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     true, 'San Francisco City Hall', 'San Francisco', 'CA', 37.7793, -122.4193),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002',
     true, 'Oakland Federal Building', 'Oakland', 'CA', 37.8044, -122.2712),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003',
     true, 'San Jose Police HQ', 'San Jose', 'CA', 37.3382, -121.8863),
    ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000004',
     true, 'Fremont City Hall', 'Fremont', 'CA', 37.5485, -121.9886),
    ('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000005',
     true, 'Berkeley Post Office', 'Berkeley', 'CA', 37.8716, -122.2727),
    -- Scattered across USA (Videos 6-10)
    ('30000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000006',
     true, 'San Antonio Strip Mall', 'San Antonio', 'TX', 29.4241, -98.4936),
    ('30000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000007',
     true, 'Leon Valley Police Department', 'Leon Valley', 'TX', 29.4952, -98.6136),
    ('30000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000008',
     true, 'Silverthorne Post Office', 'Silverthorne', 'CO', 39.6336, -106.0753),
    ('30000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000009',
     true, 'East Lansing Police Department', 'East Lansing', 'MI', 42.7370, -84.4839),
    ('30000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000010',
     true, 'Pocahontas City Hall', 'Pocahontas', 'AR', 36.2612, -90.9712)
ON CONFLICT (video_id, location_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    city = EXCLUDED.city,
    state = EXCLUDED.state,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;
