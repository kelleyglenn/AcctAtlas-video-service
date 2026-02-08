CREATE TABLE videos.video_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos.videos(id) ON DELETE CASCADE,
    location_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    display_name VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sys_period tstzrange NOT NULL DEFAULT tstzrange(NOW(), NULL),

    CONSTRAINT unique_video_location UNIQUE (video_id, location_id)
);

-- Indexes
CREATE INDEX idx_video_locations_video_id ON videos.video_locations(video_id);
CREATE INDEX idx_video_locations_location_id ON videos.video_locations(location_id);
