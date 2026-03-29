# 🔄 Data Flows

[← Back to Architecture](./ARCHITECTURE.md)

---

End-to-end data flows through the system for key use cases.

---

## Video Upload Flow

```mermaid
flowchart TD
    User[User] -->|1. Request upload| Portal[portal-service CMS]
    Portal -->|2. Call API| UploadSvc[upload-service Rust]
    UploadSvc -->|3. Generate URL| UploadSvc
    UploadSvc -->|4. Kafka| KafkaUpload[video.upload.initiated]
    User -->|5. Upload| MinIO[(MinIO)]
    User -->|6. Complete| UploadSvc
    UploadSvc -->|7. Kafka| KafkaCompleted[video.upload.completed]
    KafkaCompleted --> JobSvc[job-service Java]
    JobSvc -->|8. Create Job| JobSvc
    JobSvc -->|9. Kafka| KafkaJob[job.created]
    KafkaJob --> Worker[processing-worker Rust]
    Worker -->|10. Consume| Worker
    Worker -->|11. Transcode| Worker
    Worker -->|12. Kafka| KafkaProc[video.processing.completed]
    KafkaProc --> VideoSvc[video-service Java]
    VideoSvc -->|13. Update Status| VideoSvc
    VideoSvc -->|14. Show in Catalog| Portal
```

---

## Video Playback Flow (DRM-Protected)

```mermaid
flowchart TD
    User[User] -->|1. Request Page| Portal[portal-service CMS]
    Portal -->|2. Check DRM| Portal
    Portal -->|3. Get Token| DRMSvc[drm-service CMS]
    DRMSvc -->|4. Validate| DRMSvc
    DRMSvc -->|5. Encrypted URL| DRMSvc
    DRMSvc -->|6. DRM Token| Portal
    Portal -->|7. Player Init| Player[HLS.js + Widevine]
    Player -->|8. Request License| DRMSvc
    DRMSvc -->|9. Widevine| Widevine[Widevine Server]
    Widevine -->|10. License| DRMSvc
    DRMSvc -->|11. Play| Player
    Player -->|12. Track| Streaming[streaming-service Rust]
    Streaming -->|13. Kafka| KafkaPlayback[video.playback.started]
    KafkaPlayback --> Analytics[analytics-service Java]
```

---

## Content Moderation Flow

```mermaid
flowchart TD
    User[User] -->|1. Report| Portal[portal-service CMS]
    Portal -->|2. Kafka| KafkaReport[user.video.reported]
    KafkaReport --> CMSSvc[cms-service CMS]
    CMSSvc -->|3. Mod Queue| CMSSvc
    Mod[Moderator] -->|4. Review| CMSSvc
    CMSSvc -->|5. Decision| CMSSvc
    CMSSvc -->|6. Kafka| KafkaMod[video.moderation.approved/rejected]
    KafkaMod -->|7. If Removed| Actions[video-service, drm-service, CDN]
```

---

## Content Publishing Flow

```mermaid
flowchart TD
    Admin[Admin] -->|1. Edit| CMSSvc[cms-service CMS]
    CMSSvc -->|2. Update Metadata| CMSSvc
    CMSSvc -->|3. Set Date| CMSSvc
    CMSSvc -->|4. Kafka| KafkaMeta[video.metadata.updated]
    KafkaMeta --> Consumers[video-service, cache-service, notification-service]
    Time[Publish Time] -->|6. Publish| CMSSvc
    CMSSvc -->|7. Kafka| KafkaPub[video.published]
    KafkaPub --> Portal[portal-service CMS]
```

---

## Service Communication Patterns

### Synchronous (REST APIs)

Used for request-response operations:

- User-facing APIs (portal → services)
- Admin operations (CMS → services)
- Health checks

**Example:**

```mermaid
sequenceDiagram
    participant Portal as portal-service (CMS)
    participant Video as video-service (Java)
    Portal->>Video: HTTP GET /api/videos/123
    Video-->>Portal: 200 OK (JSON)
```

### Asynchronous (Kafka Events)

Used for event notifications and background processing:

- Video lifecycle events
- Analytics tracking
- Job orchestration
- Audit logging

**Example:**

```mermaid
graph LR
    Upload[upload-service Rust] -->|video.upload.completed| Kafka((Kafka))
    Kafka --> Job[job-service Java]
    Kafka --> Video[video-service Java]
    Kafka --> Analytics[analytics-service Java]
```

---

[← Events](./EVENTS.md) | [← Back to Architecture](./ARCHITECTURE.md) | [Next: Infrastructure →](./INFRASTRUCTURE.md)

## 📖 Related Documentation

### ⚙️ Service Documentation

- [🦀 Rust Services](./SERVICES_RUST.md) - High-performance I/O layer
- [☕ Java Services](./SERVICES_JAVA.md) - Business logic layer
- [🐘 CMS Services](./SERVICES_CMS.md) - User-facing layer

### Communication & Data

- [Event Architecture](./EVENTS.md) - Kafka topics and schemas
- [Data Flows](./DATA_FLOWS.md) - End-to-end workflows

### Operations

- [Infrastructure Components](./INFRASTRUCTURE.md) - HAProxy, Nginx, databases
- [Deployment Guide](./DEPLOYMENT.md) - Dev, staging, production setup
