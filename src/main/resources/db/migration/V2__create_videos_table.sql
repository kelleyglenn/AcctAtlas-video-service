CREATE TABLE videos.videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    youtube_id VARCHAR(11) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    thumbnail_url VARCHAR(500),
    duration_seconds INTEGER,
    channel_id VARCHAR(50),
    channel_name VARCHAR(255),
    published_at TIMESTAMPTZ,
    video_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sys_period tstzrange NOT NULL DEFAULT tstzrange(NOW(), NULL),

    CONSTRAINT valid_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DELETED'))
);

-- Amendments (element collection)
CREATE TABLE videos.video_amendments (
    video_id UUID NOT NULL REFERENCES videos.videos(id) ON DELETE CASCADE,
    amendment VARCHAR(20) NOT NULL,
    PRIMARY KEY (video_id, amendment),
    CONSTRAINT valid_amendment CHECK (amendment IN ('FIRST', 'SECOND', 'FOURTH', 'FIFTH'))
);

-- Participants (element collection)
CREATE TABLE videos.video_participants (
    video_id UUID NOT NULL REFERENCES videos.videos(id) ON DELETE CASCADE,
    participant VARCHAR(20) NOT NULL,
    PRIMARY KEY (video_id, participant),
    CONSTRAINT valid_participant CHECK (participant IN ('POLICE', 'GOVERNMENT', 'BUSINESS', 'CITIZEN'))
);

-- Indexes
CREATE INDEX idx_videos_youtube_id ON videos.videos(youtube_id);
CREATE INDEX idx_videos_status ON videos.videos(status);
CREATE INDEX idx_videos_submitted_by ON videos.videos(submitted_by);
CREATE INDEX idx_videos_created_at ON videos.videos(created_at);
