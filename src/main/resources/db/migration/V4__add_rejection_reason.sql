ALTER TABLE videos.videos
ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

COMMENT ON COLUMN videos.videos.rejection_reason IS
    'Reason provided by moderator when video is rejected';
