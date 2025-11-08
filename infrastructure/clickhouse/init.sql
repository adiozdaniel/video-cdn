-- ==========================================
-- ClickHouse CDN Platform Database Schema
-- ==========================================

-- ==========================================
-- ANALYTICS TABLES
-- ==========================================

-- Raw playback events (90-day retention)
CREATE TABLE IF NOT EXISTS video_playback_events (
    event_id UUID DEFAULT generateUUIDv4(),
    video_id String,
    user_id Nullable(String),
    session_id String,
    event_type LowCardinality(String), -- started, progress, paused, resumed, quality_changed, buffering, completed, error
    timestamp DateTime DEFAULT now(),

    -- Playback context
    current_time UInt32, -- Current playback position in seconds
    duration UInt32, -- Total video duration
    bitrate Nullable(String), -- Current quality (1080p, 720p, 480p)

    -- User context
    ip_address IPv4,
    user_agent String,
    country LowCardinality(String),
    city String,
    device_type LowCardinality(String), -- desktop, mobile, tablet, tv
    os LowCardinality(String),
    browser LowCardinality(String),

    -- Performance metrics
    buffer_duration Nullable(UInt32), -- Buffering time in ms
    startup_time Nullable(UInt32), -- Startup time in ms

    -- Additional metadata
    metadata String -- JSON for extensibility
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (video_id, timestamp, session_id)
TTL timestamp + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- Video hourly statistics (materialized view)
CREATE MATERIALIZED VIEW IF NOT EXISTS video_hourly_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (video_id, hour)
AS SELECT
    video_id,
    toStartOfHour(timestamp) AS hour,
    count() AS total_events,
    uniq(session_id) AS unique_viewers,
    countIf(event_type = 'started') AS starts,
    countIf(event_type = 'completed') AS completions,
    avg(current_time) AS avg_watch_time,
    avgIf(buffer_duration, event_type = 'buffering') AS avg_buffer_duration,
    avgIf(startup_time, event_type = 'started') AS avg_startup_time
FROM video_playback_events
GROUP BY video_id, hour;

-- User engagement daily statistics (materialized view)
CREATE MATERIALIZED VIEW IF NOT EXISTS user_engagement_daily
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (user_id, date)
AS SELECT
    user_id,
    toDate(timestamp) AS date,
    count() AS total_events,
    uniq(video_id) AS unique_videos_watched,
    sum(current_time) AS total_watch_time_seconds,
    countIf(event_type = 'completed') AS videos_completed,
    uniq(session_id) AS sessions
FROM video_playback_events
WHERE user_id IS NOT NULL
GROUP BY user_id, date;

-- Geographic statistics daily (materialized view)
CREATE MATERIALIZED VIEW IF NOT EXISTS geographic_stats_daily
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (country, city, date)
AS SELECT
    country,
    city,
    toDate(timestamp) AS date,
    count() AS total_events,
    uniq(user_id) AS unique_users,
    uniq(video_id) AS unique_videos,
    sum(current_time) AS total_watch_time_seconds
FROM video_playback_events
GROUP BY country, city, date;

-- CDN performance hourly (materialized view)
CREATE MATERIALIZED VIEW IF NOT EXISTS cdn_performance_hourly
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour)
AS SELECT
    toStartOfHour(timestamp) AS hour,
    count() AS total_requests,
    uniq(session_id) AS unique_sessions,
    avgIf(buffer_duration, event_type = 'buffering') AS avg_buffer_duration,
    avgIf(startup_time, event_type = 'started') AS avg_startup_time,
    countIf(event_type = 'buffering') AS buffer_events,
    countIf(event_type = 'error') AS error_events,
    (countIf(event_type = 'completed') / countIf(event_type = 'started')) * 100 AS completion_rate
FROM video_playback_events
GROUP BY hour;

-- ==========================================
-- CMS TABLES
-- ==========================================

-- CMS Users
CREATE TABLE IF NOT EXISTS cms_users (
    id UUID DEFAULT generateUUIDv4(),
    email String,
    name String,
    password_hash String,
    role LowCardinality(String), -- super_admin, admin, editor, moderator
    is_active UInt8 DEFAULT 1,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now(),
    last_login Nullable(DateTime),
    metadata String -- JSON for additional fields
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- Roles and Permissions
CREATE TABLE IF NOT EXISTS cms_roles (
    id UUID DEFAULT generateUUIDv4(),
    name String,
    permissions Array(String), -- Array of permission strings
    description String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- Activity Logs
CREATE TABLE IF NOT EXISTS cms_activity_logs (
    id UUID DEFAULT generateUUIDv4(),
    user_id String,
    action LowCardinality(String), -- created, updated, deleted, published, etc.
    entity_type LowCardinality(String), -- video, playlist, user, etc.
    entity_id String,
    timestamp DateTime DEFAULT now(),
    ip_address IPv4,
    metadata String -- JSON with details
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, user_id)
TTL timestamp + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- Playlists
CREATE TABLE IF NOT EXISTS cms_playlists (
    id UUID DEFAULT generateUUIDv4(),
    title String,
    description String,
    video_ids Array(String), -- Ordered array of video IDs
    thumbnail_url String,
    is_public UInt8 DEFAULT 1,
    created_by String, -- User ID
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- Moderation Queue
CREATE TABLE IF NOT EXISTS moderation_queue (
    id UUID DEFAULT generateUUIDv4(),
    video_id String,
    status LowCardinality(String), -- pending, approved, rejected
    reported_by Nullable(String), -- User ID
    reason String,
    moderator_id Nullable(String),
    moderator_notes String,
    created_at DateTime DEFAULT now(),
    reviewed_at Nullable(DateTime)
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (status, created_at)
SETTINGS index_granularity = 8192;

-- ==========================================
-- USER DATA TABLES (Portal)
-- ==========================================

-- Portal Users
CREATE TABLE IF NOT EXISTS portal_users (
    id UUID DEFAULT generateUUIDv4(),
    email String,
    name String,
    password_hash String,
    subscription_tier LowCardinality(String), -- free, basic, premium, enterprise
    subscription_expires_at Nullable(DateTime),
    is_active UInt8 DEFAULT 1,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now(),
    last_login Nullable(DateTime)
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (id)
SETTINGS index_granularity = 8192;

-- Watch History
CREATE TABLE IF NOT EXISTS watch_history (
    id UUID DEFAULT generateUUIDv4(),
    user_id String,
    video_id String,
    watched_at DateTime DEFAULT now(),
    watch_duration UInt32, -- Seconds watched
    completed UInt8, -- 0 or 1
    last_position UInt32 -- Last playback position in seconds
)
ENGINE = ReplacingMergeTree(watched_at)
PARTITION BY toYYYYMM(watched_at)
ORDER BY (user_id, video_id, watched_at)
SETTINGS index_granularity = 8192;

-- Favorites / Watchlist
CREATE TABLE IF NOT EXISTS user_favorites (
    id UUID DEFAULT generateUUIDv4(),
    user_id String,
    video_id String,
    added_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(added_at)
ORDER BY (user_id, video_id)
SETTINGS index_granularity = 8192;

-- Comments
CREATE TABLE IF NOT EXISTS video_comments (
    id UUID DEFAULT generateUUIDv4(),
    video_id String,
    user_id String,
    parent_comment_id Nullable(String), -- For replies
    content String,
    likes UInt32 DEFAULT 0,
    dislikes UInt32 DEFAULT 0,
    is_deleted UInt8 DEFAULT 0,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (video_id, created_at)
SETTINGS index_granularity = 8192;

-- User Ratings
CREATE TABLE IF NOT EXISTS video_ratings (
    id UUID DEFAULT generateUUIDv4(),
    video_id String,
    user_id String,
    rating UInt8, -- 1-5 stars or like/dislike (0/1)
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (video_id, user_id)
SETTINGS index_granularity = 8192;

-- ==========================================
-- DRM TABLES
-- ==========================================

-- DRM License Audit Logs
CREATE TABLE IF NOT EXISTS drm_license_logs (
    id UUID DEFAULT generateUUIDv4(),
    user_id String,
    video_id String,
    device_id String,
    license_type LowCardinality(String), -- widevine, playready, fairplay
    license_id String,
    issued_at DateTime DEFAULT now(),
    expires_at DateTime,
    ip_address IPv4,
    revoked UInt8 DEFAULT 0,
    revoked_at Nullable(DateTime)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(issued_at)
ORDER BY (user_id, issued_at)
TTL issued_at + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- Device Tracking
CREATE TABLE IF NOT EXISTS user_devices (
    id UUID DEFAULT generateUUIDv4(),
    user_id String,
    device_id String,
    device_name String,
    device_type LowCardinality(String), -- phone, tablet, tv, browser
    os String,
    registered_at DateTime DEFAULT now(),
    last_used_at DateTime DEFAULT now(),
    is_active UInt8 DEFAULT 1
)
ENGINE = ReplacingMergeTree(last_used_at)
ORDER BY (user_id, device_id)
SETTINGS index_granularity = 8192;

-- ==========================================
-- AGGREGATION VIEWS FOR QUICK ACCESS
-- ==========================================

-- Most watched videos (last 7 days)
CREATE MATERIALIZED VIEW IF NOT EXISTS trending_videos
ENGINE = AggregatingMergeTree()
ORDER BY (video_id)
AS SELECT
    video_id,
    uniqState(session_id) AS unique_viewers,
    countState() AS total_views,
    avgState(current_time) AS avg_watch_time
FROM video_playback_events
WHERE timestamp >= now() - INTERVAL 7 DAY
    AND event_type = 'started'
GROUP BY video_id;

-- ==========================================
-- INDEXES FOR PERFORMANCE
-- ==========================================

-- Add secondary indexes for common queries
ALTER TABLE video_playback_events ADD INDEX idx_user_id user_id TYPE bloom_filter GRANULARITY 1;
ALTER TABLE video_playback_events ADD INDEX idx_country country TYPE set(100) GRANULARITY 1;
ALTER TABLE video_playback_events ADD INDEX idx_device_type device_type TYPE set(10) GRANULARITY 1;

-- ==========================================
-- DATABASE USERS AND PERMISSIONS
-- ==========================================

-- Create application user
-- Note: ClickHouse permissions are managed differently in newer versions
-- Adjust based on your ClickHouse version and security requirements
