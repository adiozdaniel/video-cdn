# 📡 Event Architecture

[← Back to Architecture](./ARCHITECTURE.md)

---

All services communicate asynchronously via Kafka events for loose coupling and scalability.

---

## Kafka Topics

### Upload Events
```
video.upload.initiated     - Upload URL generated
video.upload.completed     - File upload finished
video.upload.cancelled     - Upload cancelled
```

### Processing Events
```
video.processing.started   - Worker picked up job
video.processing.progress  - Progress update (10% intervals)
video.processing.completed - Transcoding complete
video.processing.failed    - Processing failed
```

### Metadata Events
```
video.metadata.updated     - CMS updated metadata
video.published            - Video went live
video.unpublished          - Video taken down
video.scheduled            - Publish scheduled
video.deleted              - Video deleted
```

### Playback Events
```
video.playback.started         - User started watching
video.playback.progress        - Playback position update
video.playback.quality_changed - ABR quality switch
video.playback.buffering       - Player buffering
video.playback.completed       - Video finished
video.playback.error           - Playback error
```

### Job Events
```
job.created            - New processing job
job.status.changed     - Job status update
job.failed             - Job failure
job.retry.attempted    - Retry attempt
```

### DRM Events
```
drm.license.issued         - License generated
drm.license.revoked        - License revoked
drm.device.registered      - Device registered
drm.concurrent.limit_exceeded - Too many streams
```

### User Events
```
user.video.liked          - User liked video
user.video.commented      - User commented
user.video.reported       - Content reported
user.subscription.created - New subscription
```

### Infrastructure Events
```
cdn.cache.hit          - CDN cache hit
cdn.cache.miss         - CDN cache miss
worker.health.degraded - Worker health issue
```

---

## Event Schemas

### video.upload.completed
```json
{
  "videoId": "uuid",
  "userId": "uuid",
  "filename": "string",
  "size": "long",
  "mimeType": "string",
  "uploadedAt": "timestamp"
}
```

### video.processing.completed
```json
{
  "videoId": "uuid",
  "jobId": "uuid",
  "duration": "int",
  "variants": [
    {
      "quality": "1080p",
      "bitrate": "5000k",
      "resolution": "1920x1080",
      "size": "long",
      "path": "string"
    }
  ],
  "thumbnails": ["url1", "url2", "url3"],
  "processedAt": "timestamp",
  "processingTimeMs": "long"
}
```

### video.playback.started
```json
{
  "sessionId": "uuid",
  "videoId": "uuid",
  "userId": "uuid|null",
  "quality": "720p",
  "drmType": "widevine|playready|fairplay|none",
  "device": "desktop|mobile|tv",
  "location": {
    "country": "string",
    "region": "string"
  },
  "startedAt": "timestamp"
}
```

---

## Kafka Configuration

### Deployment
- 3+ brokers for HA
- Zookeeper or KRaft mode
- Replication factor: 3
- Min in-sync replicas: 2

### Retention
- Default: 7 days
- Analytics topics: 30 days
- Audit topics: 90 days

### Partitioning
- Partition by `videoId` for ordering
- 10 partitions per topic (scalable to 50)

---

[← PHP Services](./SERVICES_PHP.md) | [← Back to Architecture](./ARCHITECTURE.md) | [Next: Data Flows →](./DATA_FLOWS.md)
