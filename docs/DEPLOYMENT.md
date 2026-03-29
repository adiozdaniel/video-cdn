# 🚀 Deployment Guide

[← Back to Architecture](./ARCHITECTURE.md)

---

Deployment strategies for different environments.

---

## Development (Docker Compose)

### Setup

```bash
# Clone repository
git clone <repo-url>
cd cdn

# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f upload-service
```

### Architecture

- All services on single host
- Shared PostgreSQL and Redis
- Single Kafka broker
- Suitable for development and testing

---

## Staging (Kubernetes)

### Prerequisites

- Kubernetes cluster (1.28+)
- kubectl configured
- Helm 3.x

### Deploy Services

```bash
# Create namespaces
kubectl create namespace cdn-rust
kubectl create namespace cdn-java
kubectl create namespace cdn-cms
kubectl create namespace cdn-infra

# Deploy infrastructure
helm install postgres bitnami/postgresql -n cdn-infra
helm install redis bitnami/redis -n cdn-infra
helm install kafka bitnami/kafka -n cdn-infra

# Deploy services
kubectl apply -f k8s/rust-services.yaml
kubectl apply -f k8s/java-services.yaml
kubectl apply -f k8s/CMS-services.yaml
```

### Deployed Architecture

- Separate namespaces per language
- PostgreSQL Cloud instance
- Redis Sentinel (3 nodes)
- Kafka 3-broker cluster
- MinIO distributed (4 nodes)

---

## Production (Multi-Region)

### Global Architecture

```txt
┌─────────────────────────────────────┐
│  Global Load Balancer (Route53)     │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       │               │
┌──────▼─────┐  ┌─────▼──────┐
│ Region: US │  │ Region: EU │
├────────────┤  ├────────────┤
│ • HAProxy  │  │ • HAProxy  │
│ • Services │  │ • Services │
│ • Redis    │  │ • Redis    │
└──────┬─────┘  └──────┬─────┘
       │                │
       └────────┬───────┘
                │
        ┌───────▼────────┐
        │  Global Layer   │
        ├─────────────────┤
        │ • Kafka Cluster │
        │ • PostgreSQL    │
        │ • MinIO (dist)  │
        └─────────────────┘
```

### Regions

- **US-East** (Primary) - Main processing
- **EU-West** (Secondary) - European users
- **Asia-Pacific** (Future) - Asian users

### Global Components

- **Kafka:** Multi-region replication
- **PostgreSQL:** Primary in US, replicas in EU
- **MinIO:** Cross-region replication
- **CDN:** Cloudflare or Fastly

---

## Monitoring & Observability

### Metrics (Prometheus + Grafana)

```bash
# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack

# Access Grafana
kubectl port-forward svc/prometheus-grafana 3000:80
```

**Metrics Tracked:**

- Service health
- Request rates and latencies
- Error rates
- Business metrics (uploads, views)
- Infrastructure (CPU, memory, disk)

### Logging (ELK Stack)

```bash
# Install Elasticsearch
helm install elasticsearch elastic/elasticsearch

# Install Kibana
helm install kibana elastic/kibana

# Install Filebeat (log shipper)
helm install filebeat elastic/filebeat
```

**Log Sources:**

- Application logs (JSON structured)
- Access logs (Nginx, HAProxy)
- Audit logs (CMS, DRM)
- Error logs

### Tracing (Jaeger)

```bash
# Install Jaeger
helm install jaeger jaegertracing/jaeger

# Access UI
kubectl port-forward svc/jaeger-query 16686:16686
```

**Trace Paths:**

- Upload flow (10+ spans)
- Processing flow (15+ spans)
- Playback flow (8+ spans)

### Alerting (PagerDuty)

**Alert Rules:**

- Service downtime (critical)
- Error rate > 5% (warning)
- Job processing failures (warning)
- DRM license issues (critical)
- Storage capacity > 80% (warning)

---

## Security

### Authentication & Authorization

- **JWT tokens** for API access
- **OAuth2** for third-party integrations
- **RBAC** (Role-Based Access Control)
- **API rate limiting** (1000 req/min per user)

### Data Protection

- **Encryption at rest:** AES-256
- **Encryption in transit:** TLS 1.3
- **DRM:** Widevine/PlayReady/FairPlay
- **Key management:** HashiCorp Vault

### Network Security

- **VPC** with private subnets
- **Security groups** and firewalls
- **DDoS protection** (Cloudflare)
- **WAF** for API gateway

---

## Scaling Strategies

### Horizontal Scaling

```bash
# Scale Rust services
kubectl scale deployment upload-service --replicas=5

# Scale Java services
kubectl scale deployment video-service --replicas=3

# Scale CMS services
kubectl scale deployment cms-service --replicas=2

# Scale workers
kubectl scale deployment processing-worker --replicas=10
```

### Vertical Scaling

```yaml
# Increase resources for high-load services
resources:
  requests:
    cpu: "2"
    memory: "4Gi"
  limits:
    cpu: "4"
    memory: "8Gi"
```

### Auto-Scaling

```yaml
# HPA (Horizontal Pod Autoscaler)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: upload-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: upload-service
  minReplicas: 2
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

## Backup & Disaster Recovery

### Database Backups

```bash
# Automated daily backups
pg_dump -h postgres -U cdn_app cdn > backup_$(date +%Y%m%d).sql

# Upload to S3
aws s3 cp backup_*.sql s3://cdn-backups/postgres/
```

### MinIO Backups

```bash
# Mirror to backup region
mc mirror source-minio/videos backup-minio/videos

# Versioning enabled for recovery
```

### Kafka Event Replay

- Retain events for 30 days
- Replay from specific offset
- Rebuild state from events

---

## Performance Tuning

### Database Optimization

- Connection pooling (PgBouncer)
- Read replicas for analytics
- Partitioning for high-volume tables
- Optimized indexes

### Cache Optimization

- Redis cluster for high availability
- Cache warming strategies
- TTL configuration
- Cache invalidation patterns

### CDN Optimization

- Edge locations near users
- Intelligent prefetching
- HTTP/2 and HTTP/3
- Brotli compression

---

[← Infrastructure](./INFRASTRUCTURE.md) | [← Back to Architecture](./ARCHITECTURE.md) | [Back to Docs Home →](./README.md)
