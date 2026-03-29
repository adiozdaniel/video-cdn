# 📐 Complete Architecture Diagram

[← Back to Architecture](./ARCHITECTURE.md)

---

## Full System Architecture

```mermaid
graph TD
    subgraph External[External Layer]
        Users[End Users Browsers]
        Admins[Admins CMS Panel]
        Mods[Moderators CMS Panel]
        ContentMgr[Content Mgr CMS Panel]
    end

    subgraph LB[Load Balancer Layer]
        HAProxy[HAProxy Port 80]
    end

    External --> HAProxy

    subgraph App[Application Services]
        direction TB
        subgraph RustSvc[Rust Services I/O Layer]
            Upload[upload-service]
            Worker[processing-worker]
            Streaming[streaming-service]
        end
        subgraph JavaSvc[Java Services Business Logic]
            Video[video-service]
            Job[job-service]
            Analytics[analytics-service]
        end
        subgraph CMSSvc[CMS Services User-Facing]
            DRM[drm-service]
            CMS[cms-service]
            Portal[portal-service]
        end
    end

    HAProxy --> RustSvc
    HAProxy --> JavaSvc
    HAProxy --> CMSSvc

    subgraph EventLayer[Event Streaming Layer]
        Kafka((Apache Kafka KRaft Mode))
    end

    RustSvc <--> Kafka
    JavaSvc <--> Kafka
    CMSSvc <--> Kafka

    subgraph Storage[Data Storage Layer]
        PG[(PostgreSQL)]
        CH[(ClickHouse)]
        Redis[(Redis)]
        MinIO[(MinIO)]
        Nginx[Nginx CDN]
    end

    JavaSvc --> PG
    CMSSvc --> CH
    Analytics --> CH
    Streaming --> Redis
    Upload --> MinIO
    Worker --> MinIO
    Nginx --> MinIO

    subgraph Observability[Observability Layer]
        Grafana[Grafana]
        Prometheus[Prometheus]
        ELK[ELK Stack]
    end

    App -.-> Prometheus
    App -.-> ELK
    Prometheus --> Grafana
    CH --> Grafana
    PG --> Grafana
```

---

## Data Flow Diagrams

### 1. Video Upload Flow

```mermaid
sequenceDiagram
    participant User
    participant Portal as portal-service (CMS)
    participant Upload as upload-service (Rust)
    participant MinIO
    participant Kafka
    participant Job as job-service (Java)
    participant Worker as processing-worker (Rust)
    participant Video as video-service (Java)

    User->>Portal: 1. Request upload
    Portal->>Upload: 2. Call API
    Upload->>Kafka: video.upload.initiated
    Upload-->>User: 3. Presigned URL
    User->>MinIO: 4. Upload directly (20+ Gbps)
    MinIO->>Upload: 5. Upload complete
    Upload->>Kafka: video.upload.completed
    Kafka->>Job: Consume event
    Job->>Kafka: job.created
    Kafka->>Worker: Consume event
    Worker->>Worker: Transcode, Thumbnails, HLS
    Worker->>Kafka: video.processing.completed
    Kafka->>Video: Status: READY
    Note over User,Video: Video appears in catalog!
```

---

### 2. Video Playback Flow (DRM)

```mermaid
sequenceDiagram
    participant User
    participant Portal as portal-service (CMS)
    participant DRM as drm-service (CMS)
    participant Player as Video Player (HLS.js)
    participant License as DRM License Server
    participant Streaming as streaming-service (Rust)
    participant Kafka
    participant Analytics as analytics-service (Java)

    User->>Portal: 1. Browse catalog
    User->>Portal: 2. Select video
    Portal->>DRM: 3. Check entitlement & Generate token
    DRM-->>User: Manifest URL + Token
    User->>Player: Load video
    Player->>DRM: 4. Request license
    DRM->>License: Proxy license request
    License-->>DRM: License response
    DRM-->>Player: 5. Return license
    Player->>Player: Decrypt & Play
    Player->>Streaming: 6. Playback events
    Streaming->>Kafka: video.playback.*
    Kafka->>Analytics: Consume & Write to ClickHouse
```

---

### 3. Analytics Pipeline

```mermaid
graph TD
    Kafka((Kafka Playback Events))
    
    Analytics[analytics-service Java]

    Kafka --> Analytics
    
    CH[(ClickHouse Raw Events)]
    MV[Materialized Views]
    Redis[(Redis Real-time)]
    
    Analytics --> CH
    Analytics --> Redis
    
    CH --> MV
    MV --> Stats1[video_hourly_stats]
    MV --> Stats2[user_engagement_daily]
    MV --> Stats3[geographic_stats_daily]
    MV --> Stats4[cdn_performance_hourly]
    
    Grafana[Grafana Dashboards]
    
    Redis --> Grafana
    MV --> Grafana
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
