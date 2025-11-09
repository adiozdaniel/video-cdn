# Phase 3: Specialized Worker Pools Architecture

## Overview

Phase 3 introduces specialized worker pools where each worker focuses on a single quality profile. This enables horizontal scaling and better resource utilization.

**Goal**: Enable independent scaling of workers by quality tier

## Architecture Diagram

```
Upload Service
    ↓
Redis: processing-jobs (trigger)
    ↓
📋 Orchestrator Service (NEW)
    ├─ Probe video (ffprobe)
    ├─ Decide profiles adaptively
    ├─ Publish jobs to profile-specific streams
    └─ Track completion state
    ↓
Redis Streams (Profile-Specific):
├─ processing-jobs-480p  → 480p-workers (×4)
├─ processing-jobs-720p  → 720p-workers (×2)
├─ processing-jobs-1080p → 1080p-workers (×1)
├─ processing-jobs-360p  → 360p-workers (×2)
└─ processing-jobs-240p  → 240p-workers (×2)
    ↓
🔧 Specialized Workers (Modified)
    ├─ Download input video
    ├─ Transcode SINGLE profile only
    ├─ Upload HLS files for that profile
    └─ Publish completion event
    ↓
Redis: processing-jobs-completed
    ↓
📋 Orchestrator
    ├─ Track which profiles completed
    ├─ Update video status (PLAYABLE after 480p, READY after all)
    └─ Generate/update master playlist
```

## Components

### 1. Orchestrator Service (NEW)

**Responsibilities:**
- Listen for new upload events
- Probe video metadata (resolution, duration)
- Decide which profiles to generate (adaptive)
- Create profile-specific jobs
- Publish jobs to appropriate Redis Streams
- Track completion of all profiles
- Update video status (PLAYABLE → READY)
- Generate master playlists

**Technology**: Rust (for consistency with workers)

**State Management**: Stateless - all state in PostgreSQL + Redis

**Key Functions:**
```rust
// Orchestrator main flow
async fn handle_video_uploaded(video_id: Uuid) {
    // 1. Download video temporarily (or get from MinIO)
    // 2. Probe with ffprobe
    // 3. Decide profiles
    // 4. Publish 480p job (high priority)
    // 5. Publish other profile jobs
    // 6. Store expected profile count in DB
}

async fn handle_profile_completed(event: ProfileCompletedEvent) {
    // 1. Update profile completion in DB
    // 2. If 480p: Mark video PLAYABLE + generate basic master playlist
    // 3. If all complete: Mark video READY + generate full master playlist
}
```

### 2. Redis Streams Structure

**Input Streams** (profile-specific jobs):
```
processing-jobs-480p   - Highest priority, most workers
processing-jobs-720p   - Medium priority
processing-jobs-1080p  - Lowest priority, heaviest
processing-jobs-360p   - Medium priority
processing-jobs-240p   - Highest priority (lightweight)
```

**Output Stream** (completion events):
```
processing-jobs-completed  - All workers publish here
```

**Job Format** (profile-specific):
```json
{
  "videoId": "uuid",
  "profile": "480p",
  "inputPath": "raw/uuid/filename.mp4",
  "outputPath": "hls/uuid/",
  "preset": "ultrafast",
  "priority": "high",
  "timestamp": 1699999999
}
```

**Completion Event Format**:
```json
{
  "videoId": "uuid",
  "profile": "480p",
  "workerId": "worker-480p-1",
  "status": "COMPLETED",
  "filesUploaded": 127,
  "durationMs": 45000,
  "timestamp": 1699999999
}
```

### 3. Specialized Workers (Modified)

**Changes from Phase 2**:
- Workers now consume from **single profile stream**
- Process only ONE quality level per job
- Simpler logic - no orchestration needed
- Publish completion event when done

**Worker Types**:
```
480p-worker  - ENV: PROFILE=480p, STREAM=processing-jobs-480p
720p-worker  - ENV: PROFILE=720p, STREAM=processing-jobs-720p
1080p-worker - ENV: PROFILE=1080p, STREAM=processing-jobs-1080p
360p-worker  - ENV: PROFILE=360p, STREAM=processing-jobs-360p
240p-worker  - ENV: PROFILE=240p, STREAM=processing-jobs-240p
```

**Worker Scaling**:
```yaml
# docker-compose.yml
services:
  worker-480p:
    replicas: 4  # Most workers for fastest profile
  worker-720p:
    replicas: 2
  worker-1080p:
    replicas: 1  # Fewest workers for slowest profile
  worker-360p:
    replicas: 2
  worker-240p:
    replicas: 2
```

## Benefits

### 1. Independent Scaling
- Scale 480p workers (×10) for instant playback
- Scale 1080p workers (×2) only when needed
- Add/remove workers without affecting others

### 2. Better Resource Utilization
- 480p workers use less CPU/memory
- 1080p workers get dedicated resources
- No CPU contention between profiles

### 3. Fault Isolation
- 1080p worker crash doesn't affect 480p
- Failed profiles can be retried independently
- Partial success (480p works, 1080p fails)

### 4. Priority Control
- Process 480p first (PLAYABLE in ~2 min)
- Process 360p/240p next (mobile support)
- Process 720p/1080p last (desktop HD)

### 5. Simpler Worker Logic
- Each worker does ONE thing
- Easier to debug and monitor
- No complex orchestration in worker

## Performance Comparison

### Phase 2 (Single Worker)
```
10-minute video:
├─ 480p: 2 min (PLAYABLE)
├─ Other profiles in parallel: 6 min
└─ Total: ~8 min to READY
```

### Phase 3 (Specialized Workers)
```
10-minute video:
├─ 480p: 1.5 min (PLAYABLE) ← Dedicated worker
├─ 360p: 1.5 min (parallel)  ← Different worker
├─ 720p: 2.5 min (parallel)  ← Different worker
├─ 1080p: 4 min (parallel)   ← Different worker
└─ Total: ~4 min to READY (all profiles parallel!)
```

**Speedup**: 2x faster to READY, slightly faster to PLAYABLE

## Database Schema Changes

### Add profile tracking table:

```sql
CREATE TABLE video_profile_jobs (
    id SERIAL PRIMARY KEY,
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    profile VARCHAR(10) NOT NULL, -- 480p, 720p, etc.
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    worker_id VARCHAR(50),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    files_count INTEGER,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(video_id, profile)
);

CREATE INDEX idx_profile_jobs_video ON video_profile_jobs(video_id);
CREATE INDEX idx_profile_jobs_status ON video_profile_jobs(status) WHERE status IN ('QUEUED', 'PROCESSING');
```

## Implementation Phases

### Step 1: Create Orchestrator Service
- Scaffold Rust service
- Add ffprobe integration
- Implement Redis Stream publisher
- Add completion event consumer

### Step 2: Modify Workers
- Add PROFILE environment variable
- Consume from single stream
- Process single profile only
- Publish completion events

### Step 3: Docker Compose Configuration
- Add orchestrator service
- Create multiple worker instances with different profiles
- Configure resource limits per worker type

### Step 4: Testing
- Upload test video
- Verify orchestrator creates jobs
- Verify specialized workers process correctly
- Verify completion tracking works

## Migration Strategy

### Phase 2 → Phase 3 (Zero Downtime):

1. Deploy orchestrator (passive mode - just logs)
2. Workers continue using Phase 2 logic
3. Verify orchestrator creates correct jobs
4. Switch workers to Phase 3 mode (profile-specific)
5. Disable Phase 2 worker logic
6. Remove old `processing-jobs` stream

## Future: Phase 4 Enhancement

Phase 4 (Distributed Chunking) builds on Phase 3:
- Orchestrator splits long videos into chunks
- Publishes multiple jobs per profile (one per chunk)
- Workers process chunks in parallel
- Orchestrator concatenates results

```
10-minute video → 5 chunks (2 min each)
├─ 480p: 5 workers × 30s each = 30s total!
├─ 720p: 5 workers × 45s each = 45s total!
└─ 1080p: 5 workers × 90s each = 90s total!
```

**Result**: 10-minute video fully processed in ~90 seconds!

## Monitoring

Key metrics to track:
- Worker utilization by profile
- Queue depth per profile stream
- Time to PLAYABLE (480p completion)
- Time to READY (all profiles completion)
- Worker failure rate by profile
- Profile generation rate (jobs/sec)

## Cost Optimization

Worker sizing:
```
480p-workers: 1 CPU, 1GB RAM   (cheap, many instances)
720p-workers: 2 CPU, 2GB RAM   (moderate)
1080p-workers: 4 CPU, 4GB RAM  (expensive, few instances)
```

This allows efficient resource allocation based on workload complexity.
