-- CDN Platform Database Schema
-- Optimized for high throughput with indexes and partitioning

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Videos table - Main entity
CREATE TABLE videos (
    id VARCHAR(36) PRIMARY KEY,
    filename VARCHAR(512) NOT NULL,
    size BIGINT NOT NULL,
    duration INTEGER, -- seconds
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    uploaded_at TIMESTAMP,
    processed_at TIMESTAMP,
    deleted_at TIMESTAMP,
    metadata JSONB, -- Additional metadata (resolution, codec, etc.)
    CONSTRAINT status_check CHECK (status IN ('UPLOADING', 'UPLOADED', 'QUEUED', 'PROCESSING', 'READY', 'FAILED', 'DELETED'))
);

-- Index for listing queries (most common)
CREATE INDEX idx_videos_status_created ON videos(status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_videos_created ON videos(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_videos_uploaded ON videos(uploaded_at DESC) WHERE uploaded_at IS NOT NULL;

-- Processing jobs table
CREATE TABLE processing_jobs (
    id SERIAL PRIMARY KEY,
    video_id VARCHAR(36) NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    worker_id VARCHAR(50),
    progress INTEGER DEFAULT 0, -- 0-100
    message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT job_status_check CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Index for job queries
CREATE INDEX idx_jobs_video ON processing_jobs(video_id);
CREATE INDEX idx_jobs_status ON processing_jobs(status, created_at) WHERE status IN ('QUEUED', 'PROCESSING');
CREATE INDEX idx_jobs_worker ON processing_jobs(worker_id) WHERE worker_id IS NOT NULL;

-- Video variants table (different bitrates)
CREATE TABLE video_variants (
    id SERIAL PRIMARY KEY,
    video_id VARCHAR(36) NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    name VARCHAR(20) NOT NULL, -- 1080p, 720p, 480p
    resolution VARCHAR(20) NOT NULL, -- 1920x1080
    bitrate VARCHAR(20) NOT NULL, -- 5000k
    codec VARCHAR(20) NOT NULL, -- h264
    path VARCHAR(1024) NOT NULL, -- MinIO path
    size BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(video_id, name)
);

CREATE INDEX idx_variants_video ON video_variants(video_id);

-- Analytics events table (high-volume inserts)
CREATE TABLE analytics_events (
    id BIGSERIAL,
    video_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- play, pause, buffer, error, quality_change
    user_id VARCHAR(36),
    session_id VARCHAR(36) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB, -- Additional event data
    ip_address INET,
    user_agent TEXT,
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create partitions for analytics (monthly)
CREATE TABLE analytics_events_2025_11 PARTITION OF analytics_events
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE analytics_events_2025_12 PARTITION OF analytics_events
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE INDEX idx_analytics_video ON analytics_events(video_id, timestamp DESC);
CREATE INDEX idx_analytics_session ON analytics_events(session_id);

-- CDN cache statistics table
CREATE TABLE cdn_stats (
    id SERIAL PRIMARY KEY,
    edge_node VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    requests_total BIGINT DEFAULT 0,
    cache_hits BIGINT DEFAULT 0,
    cache_misses BIGINT DEFAULT 0,
    bytes_sent BIGINT DEFAULT 0,
    avg_response_time_ms INTEGER,
    UNIQUE(edge_node, timestamp)
);

CREATE INDEX idx_cdn_stats_time ON cdn_stats(timestamp DESC);

-- System health metrics
CREATE TABLE worker_health (
    id SERIAL PRIMARY KEY,
    worker_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, -- healthy, degraded, down
    cpu_percent DECIMAL(5,2),
    memory_percent DECIMAL(5,2),
    active_jobs INTEGER DEFAULT 0,
    last_heartbeat TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(worker_id)
);

CREATE INDEX idx_worker_health ON worker_health(last_heartbeat DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create database user with appropriate permissions
CREATE USER cdn_app WITH PASSWORD 'cdn_password_2024';
GRANT CONNECT ON DATABASE cdn TO cdn_app;
GRANT USAGE ON SCHEMA public TO cdn_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cdn_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO cdn_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cdn_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO cdn_app;

-- Read-only user for replicas
CREATE USER cdn_readonly WITH PASSWORD 'readonly_password_2024';
GRANT CONNECT ON DATABASE cdn TO cdn_readonly;
GRANT USAGE ON SCHEMA public TO cdn_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cdn_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO cdn_readonly;
