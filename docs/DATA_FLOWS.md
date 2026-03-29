# 🔄 Data Flows

[← Back to Architecture](./ARCHITECTURE.md)

---

End-to-end data flows through the system for key use cases.

---

## Video Upload Flow

```txt
1. User → portal-service (CMS)
   ↓
2. Portal calls upload-service API (Rust)
   ↓
3. upload-service generates presigned URL
   ↓
4. Kafka: video.upload.initiated
   ↓
5. User uploads directly to MinIO
   ↓
6. User calls upload-service /complete
   ↓
7. Kafka: video.upload.completed
   ↓
8. job-service (Java) creates processing job
   ↓
9. Kafka: job.created
   ↓
10. processing-worker (Rust) consumes job
    ↓
11. Transcode video, generate variants
    ↓
12. Kafka: video.processing.completed
    ↓
13. video-service (Java) updates status to READY
    ↓
14. Video appears in portal catalog
```

---

## Video Playback Flow (DRM-Protected)

```txt
1. User → portal-service video page (CMS)
   ↓
2. Check if DRM-protected
   ↓
3. Portal calls drm-service for token
   ↓
4. drm-service validates user entitlement
   ↓
5. Generate encrypted manifest URL
   ↓
6. Return page with DRM token
   ↓
7. Player initializes (HLS.js + Widevine)
   ↓
8. Player requests license from drm-service
   ↓
9. drm-service contacts Widevine server
   ↓
10. Return license to player
    ↓
11. Player decrypts and plays video
    ↓
12. streaming-service tracks playback
    ↓
13. Kafka: video.playback.started
    ↓
14. analytics-service aggregates metrics
```

---

## Content Moderation Flow

```txt
1. User reports video in portal
   ↓
2. Kafka: user.video.reported
   ↓
3. cms-service adds to moderation queue
   ↓
4. Moderator reviews in CMS admin
   ↓
5. Decision: Approve or Remove
   ↓
6. Kafka: video.moderation.approved/rejected
   ↓
7. If removed:
   - video-service updates status
   - drm-service revokes all licenses
   - CDN purges cache
   - portal hides from catalog
```

---

## Content Publishing Flow

```txt
1. Admin edits video in cms-service
   ↓
2. Update metadata, thumbnail, tags
   ↓
3. Set publish date: "2025-11-10 10:00 AM"
   ↓
4. Kafka: video.metadata.updated
   ↓
5. Consumers:
   - video-service → Update search index
   - cache-service → Invalidate cache
   - notification-service → Schedule notification
   ↓
6. Scheduled publish time arrives
   ↓
7. cms-service → Kafka: video.published
   ↓
8. portal-service → Show in catalog
```

---

## Service Communication Patterns

### Synchronous (REST APIs)

Used for request-response operations:

- User-facing APIs (portal → services)
- Admin operations (CMS → services)
- Health checks

**Example:**

```txt
portal-service (CMS) → HTTP GET → video-service (Java)
  ← JSON response ←
```

### Asynchronous (Kafka Events)

Used for event notifications and background processing:

- Video lifecycle events
- Analytics tracking
- Job orchestration
- Audit logging

**Example:**

```txt
upload-service (Rust) → Kafka → video.upload.completed
                                      ↓
                           job-service (Java) consumes
                           video-service (Java) consumes
                           analytics-service (Java) consumes
```

---

[← Events](./EVENTS.md) | [← Back to Architecture](./ARCHITECTURE.md) | [Next: Infrastructure →](./INFRASTRUCTURE.md)
