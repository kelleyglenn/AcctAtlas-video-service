-- Add SECURITY to the valid_participant CHECK constraint
ALTER TABLE videos.video_participants
    DROP CONSTRAINT valid_participant,
    ADD CONSTRAINT valid_participant CHECK (
        participant IN ('POLICE', 'GOVERNMENT', 'BUSINESS', 'CITIZEN', 'SECURITY')
    );
