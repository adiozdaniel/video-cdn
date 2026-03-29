# 📐 Complete Architecture Diagram

[← Back to Architecture](./ARCHITECTURE.md)

---

## Full System Architecture

```txt
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    EXTERNAL LAYER                                        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐      │
│  │   End Users  │     │    Admins    │     │  Moderators  │     │  Content Mgr │      │
│  │  (Browsers)  │     │  (CMS Panel) │     │  (CMS Panel) │     │  (CMS Panel) │      │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┘      │
│         │                    │                     │                     │              │
└─────────┼────────────────────┼─────────────────────┼─────────────────────┼──────────────┘
          │                    │                     │                     │
          │                    └─────────────────────┴─────────────────────┘
          │                                          │
          ▼                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   LOAD BALANCER LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                          │
│                          ┌────────────────────────────────┐                             │
│                          │       HAProxy (Port 80)        │                             │
│                          │   - L7 Load Balancing          │                             │
│                          │   - CORS Support               │                             │
│                          │   - SSL/TLS Termination        │                             │
│                          │   - Health Checks              │                             │
│                          │   - Stats: :8404/stats         │                             │
│                          └────────────┬───────────────────┘                             │
│                                       │                                                  │
└───────────────────────────────────────┼──────────────────────────────────────────────────┘
                                        │
         ┌──────────────────────────────┼──────────────────────────────┐
         │                              │                              │
         │                              │                              │
┌────────▼────────┐            ┌────────▼────────┐            ┌───────▼────────┐
│                 │            │                 │            │                │
│  RUST SERVICES  │            │  JAVA SERVICES  │            │  CMS SERVICES  │
│  (I/O Layer)    │            │ (Business Logic)│            │ (User-Facing)  │
│                 │            │                 │            │                │
│  Port: 8080+    │            │  Port: 8081+    │            │  Port: 8082-83 │
└────────┬────────┘            └────────┬────────┘            └────────┬───────┘
         │                              │                              │
         │                              │                              │
┌────────▼──────────────────────────────▼──────────────────────────────▼────────┐
│                                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                        │
│  │   upload-    │  │ processing-  │  │  streaming-  │   RUST SERVICES         │
│  │   service    │  │   worker     │  │   service    │                         │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤                         │
│  │• Presigned   │  │• FFmpeg      │  │• HLS/DASH    │                         │
│  │  URLs        │  │  transcoding │  │  manifests   │                         │
│  │• 20+ Gbps    │  │• 3 bitrates  │  │• ABR logic   │                         │
│  │  throughput  │  │• Thumbnails  │  │• Live metrics│                         │
│  │• Validation  │  │• Parallel    │  │• Session     │                         │
│  │              │  │  processing  │  │  tracking    │                         │
│  └──────────────┘  └──────────────┘  └──────────────┘                         │
│                                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                        │
│  │   video-     │  │    job-      │  │ analytics-   │   JAVA SERVICES         │
│  │   service    │  │   service    │  │   service    │                         │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤                         │
│  │• Video CRUD  │  │• Job queue   │  │• Metrics     │                         │
│  │• Metadata    │  │• Retry logic │  │  aggregation │                         │
│  │• Search      │  │• DLQ         │  │• Dashboards  │                         │
│  │• Categories  │  │• Worker      │  │• Reports     │                         │
│  │• Permissions │  │  monitoring  │  │• ClickHouse  │                         │
│  │• Analytics   │  │• SLA track   │  │  queries     │                         │
│  └──────────────┘  └──────────────┘  └──────────────┘                         │
│                                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                        │
│  │    drm-      │  │    cms-      │  │   portal-    │   CMS SERVICES          │
│  │   service    │  │   service    │  │   service    │                         │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤                         │
│  │• Widevine    │  │• Admin panel │  │• Video       │                         │
│  │• PlayReady   │  │• Metadata    │  │  catalog     │                         │
│  │• FairPlay    │  │  editor      │  │• Search      │                         │
│  │• License     │  │• Scheduling  │  │• Player page │                         │
│  │  generation  │  │• Moderation  │  │• User        │                         │
│  │• Device mgmt │  │• Playlists   │  │  profiles    │                         │
│  └──────────────┘  └──────────────┘  └──────────────┘                         │
│                                                                                 │
└─────────────────────────────────────┬───────────────────────────────────────────┘
                                      │
                                      │ All services communicate via
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            EVENT STREAMING LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│                     ┌──────────────────────────────────┐                        │
│                     │    Apache Kafka (KRaft Mode)     │                        │
│                     │    - 3+ Brokers for HA           │                        │
│                     │    - No ZooKeeper dependency     │                        │
│                     │    - 40+ Topics                  │                        │
│                     │    - Replication: 3              │                        │
│                     └──────────────┬───────────────────┘                        │
│                                    │                                             │
│   ┌────────────────────────────────┼────────────────────────────────┐          │
│   │                                │                                │          │
│   ▼                                ▼                                ▼          │
│ ┌───────────────┐      ┌────────────────────┐         ┌──────────────────┐   │
│ │ video.upload.*│      │ video.processing.* │         │ video.playback.* │   │
│ │ video.metadata│      │ job.*              │         │ drm.*            │   │
│ │ video.published      │ worker.health.*    │         │ user.*           │   │
│ └───────────────┘      └────────────────────┘         └──────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ Events flow to
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DATA STORAGE LAYER                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐             │
│  │   PostgreSQL     │  │    ClickHouse    │  │      Redis       │             │
│  │   (Port 5432)    │  │   (Port 8123)    │  │   (Port 6379)    │             │
│  ├──────────────────┤  ├──────────────────┤  ├──────────────────┤             │
│  │ • Video metadata │  │ • Playback       │  │ • Live viewers   │             │
│  │ • Processing jobs│  │   analytics      │  │   count          │             │
│  │ • Video variants │  │ • User engagement│  │ • Trending       │             │
│  │ • Categories     │  │ • Geographic     │  │   videos         │             │
│  │                  │  │   analytics      │  │ • Session cache  │             │
│  │ Used by:         │  │ • CDN metrics    │  │ • Rate limiting  │             │
│  │ - Java services  │  │ • CMS data       │  │ • DRM tokens     │             │
│  │                  │  │ • Permissions    │  │ • Leaderboards   │             │
│  │ Partitioning:    │  │ • Materialized   │  │                  │             │
│  │ - Monthly        │  │   views          │  │ Performance:     │             │
│  │ - Read replicas  │  │                  │  │ - < 1ms latency  │             │
│  │                  │  │ Used by:         │  │ - 100K+ ops/sec  │             │
│  │                  │  │ - CMS services   │  │                  │             │
│  │                  │  │ - Analytics svc  │  │                  │             │
│  │                  │  │                  │  │                  │             │
│  │                  │  │ Performance:     │  │                  │             │
│  │                  │  │ - 90:1 compress  │  │                  │             │
│  │                  │  │ - < 100ms query  │  │                  │             │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘             │
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                                   │
│  │      MinIO       │  │     Nginx CDN    │                                   │
│  │   (Port 9000)    │  │    (Port 80)     │                                   │
│  ├──────────────────┤  ├──────────────────┤                                   │
│  │ • Original videos│  │ • Video segments │                                   │
│  │ • Processed      │  │   cache (365d)   │                                   │
│  │   variants       │  │ • Manifests      │                                   │
│  │ • Thumbnails     │  │   cache (10s)    │                                   │
│  │ • Subtitles      │  │ • 50GB cache     │                                   │
│  │                  │  │   size           │                                   │
│  │ S3-compatible    │  │ • CDN edge       │                                   │
│  │ Presigned URLs   │  │   serving        │                                   │
│  │ 20+ Gbps         │  │ • Zero-copy I/O  │                                   │
│  └──────────────────┘  └──────────────────┘                                   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ Monitoring & Visualization
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          OBSERVABILITY LAYER                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌────────────────┐    ┌─────────────────┐    ┌──────────────────┐            │
│  │   Grafana      │    │   Prometheus    │    │   ELK Stack      │            │
│  │  (Port 3000)   │    │   (Port 9090)   │    │  (Logging)       │            │
│  ├────────────────┤    ├─────────────────┤    ├──────────────────┤            │
│  │ • Dashboards   │◄───│ • Metrics       │    │ • Elasticsearch  │            │
│  │ • Real-time    │    │   collection    │    │ • Kibana         │            │
│  │   analytics    │    │ • Time-series   │    │ • Filebeat       │            │
│  │ • Alerting     │    │   storage       │    │ • Log            │            │
│  │                │    │ • Service       │    │   aggregation    │            │
│  │ Data sources:  │    │   discovery     │    │                  │            │
│  │ - ClickHouse   │    │                 │    │                  │            │
│  │ - Prometheus   │    │                 │    │                  │            │
│  │ - PostgreSQL   │    │                 │    │                  │            │
│  └────────────────┘    └─────────────────┘    └──────────────────┘            │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagrams

### 1. Video Upload Flow

```txt
┌─────────────┐
│    User     │
└──────┬──────┘
       │ 1. Request upload
       ▼
┌─────────────────┐
│ portal-service  │ (CMS)
└──────┬──────────┘
       │ 2. Call API
       ▼
┌─────────────────┐        ┌──────────┐
│ upload-service  │───────►│  Kafka   │ video.upload.initiated
└──────┬──────────┘ (Rust) └──────────┘
       │ 3. Generate presigned URL
       │
       ▼
┌─────────────┐
│    MinIO    │ ◄─── 4. User uploads directly (20+ Gbps)
└──────┬──────┘
       │ 5. Upload complete
       ▼
┌─────────────────┐        ┌──────────┐
│ upload-service  │───────►│  Kafka   │ video.upload.completed
└─────────────────┘        └────┬─────┘
                                │
                                ▼
                          ┌─────────────┐
                          │ job-service │ (Java)
                          └──────┬──────┘
                                 │ Create job
                                 ▼
                          ┌──────────┐
                          │  Kafka   │ job.created
                          └────┬─────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ processing-worker    │ (Rust)
                    │ - Transcode 3 levels │
                    │ - Generate thumbnails│
                    │ - Create HLS         │
                    └──────┬───────────────┘
                           │
                           ▼
                    ┌──────────┐
                    │  Kafka   │ video.processing.completed
                    └────┬─────┘
                         │
                         ▼
                  ┌──────────────┐
                  │video-service │ (Java)
                  │ Status: READY│
                  └──────┬───────┘
                         │
                         ▼
                  Video appears in catalog!
```

---

### 2. Video Playback Flow (DRM)

```txt
┌─────────────┐
│    User     │
└──────┬──────┘
       │ 1. Browse catalog
       ▼
┌──────────────────┐
│  portal-service  │ (CMS)
│  - Video list    │
└──────┬───────────┘
       │ 2. Select video
       ▼
┌──────────────────┐
│  drm-service     │ (CMS)
│  - Check         │
│    entitlement   │
│  - Generate      │
│    DRM token     │
└──────┬───────────┘
       │ 3. Token + manifest URL
       ▼
┌─────────────────────┐
│  Video Player       │
│  (Browser + HLS.js) │
└──────┬──────────────┘
       │ 4. Request license
       ▼
┌──────────────────┐        ┌─────────────────┐
│  drm-service     │───────►│ Widevine/       │
│                  │        │ PlayReady/      │
│                  │◄───────│ FairPlay Server │
└──────┬───────────┘        └─────────────────┘
       │ 5. Return license
       ▼
┌─────────────────────┐
│  Video Player       │
│  - Decrypt content  │
│  - Start playback   │
└──────┬──────────────┘
       │ 6. Playback events
       ▼
┌────────────────────┐        ┌──────────┐
│streaming-service   │───────►│  Kafka   │ video.playback.*
└────────────────────┘ (Rust) └────┬─────┘
                                    │
                                    ▼
                              ┌──────────────────┐
                              │ analytics-service│ (Java)
                              │ → ClickHouse     │
                              └──────────────────┘
```

---

### 3. Analytics Pipeline

```txt
┌───────────────────────────────────────┐
│        Playback Events (Kafka)        │
│  - video.playback.started             │
│  - video.playback.progress            │
│  - video.playback.quality_changed     │
│  - video.playback.buffering           │
│  - video.playback.completed           │
└────────────┬──────────────────────────┘
             │
             ▼
      ┌──────────────────┐
      │ analytics-service│ (Java/Spring)
      │ - Kafka consumer │
      │ - Batch inserts  │
      └─────┬───────┬────┘
            │       │
            │       │ Write raw events
            │       ▼
            │  ┌──────────────────────────┐
            │  │      ClickHouse          │
            │  │ video_playback_events    │
            │  │ (90-day retention)       │
            │  └────┬─────────────────────┘
            │       │
            │       │ Auto-aggregation via
            │       │ Materialized Views
            │       │
            │       ├───► video_hourly_stats
            │       ├───► user_engagement_daily
            │       ├───► geographic_stats_daily
            │       └───► cdn_performance_hourly
            │
            │ Real-time counters
            ▼
      ┌──────────────┐
      │    Redis     │
      │ - Live viewer│
      │   counts     │
      │ - Trending   │
      │   videos     │
      └──────────────┘
            │
            │ Visualization
            ▼
      ┌──────────────┐
      │   Grafana    │
      │ - Dashboards │
      │ - Alerts     │
      └──────────────┘
```

---

## Technology Stack Summary

### Layer 1: Load Balancer

- **HAProxy** - 50k connections, SSL/TLS, health checks

### Layer 2: Application Services

**Rust (I/O Performance):**

- upload-service
- processing-worker
- streaming-service

**Java (Business Logic):**

- video-service
- job-service
- analytics-service

**CMS (User Interfaces):**

- drm-service
- cms-service
- portal-service

### Layer 3: Event Streaming

- **Kafka (KRaft mode)** - 40+ topics, 3+ brokers, no ZooKeeper

### Layer 4: Data Storage

**Operational Data:**

- **PostgreSQL** - Video metadata, jobs, users (Java services)
- **ClickHouse** - CMS data, permissions, analytics (CMS services)

**Analytics:**

- **ClickHouse** - Time-series analytics (90:1 compression)
- **Redis** - Real-time counters (< 1ms)

**Object Storage:**

- **MinIO** - Videos, thumbnails (20+ Gbps)

**CDN:**

- **Nginx** - Edge caching (50GB cache)

### Layer 5: Observability

- **Grafana** - Dashboards
- **Prometheus** - Metrics
- **ELK Stack** - Centralized logging

---

## Performance Metrics

| Component | Throughput | Latency | Scale |
| --------- | ---------- | ------- | ------ |
| **Upload** | 20+ Gbps | < 2ms | 10k+ concurrent |
| **Transcoding** | 100+ videos/hr | 1x realtime | Parallel workers |
| **Streaming** | 50+ Gbps | < 1ms | 10k+ viewers |
| **ClickHouse** | 1M+ rows/sec | < 100ms | Billions of rows |
| **Redis** | 100K+ ops/sec | < 1ms | TB memory |
| **PostgreSQL** | 10K+ TPS | < 50ms | Read replicas |
| **Kafka** | 1M+ msgs/sec | < 10ms | PB scale |

---

## Security Features

- ✅ DRM (Widevine, PlayReady, FairPlay)
- ✅ JWT authentication
- ✅ OAuth2 integration
- ✅ RBAC permissions
- ✅ Rate limiting
- ✅ Encryption at rest (AES-256)
- ✅ Encryption in transit (TLS 1.3)
- ✅ CORS configuration
- ✅ SQL injection prevention
- ✅ API key management

---

[← Back to Architecture](./ARCHITECTURE.md) | [Back to Docs Home →](./README.md)
