#!/bin/bash

# ==========================================
# Kafka Topic Initialization Script
# Creates all 40+ topics for CDN platform
# ==========================================

set -e

KAFKA_BROKER="${KAFKA_BROKER:-kafka-1:9092}"
PARTITIONS="${PARTITIONS:-10}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-3}"

echo "==========================================
Kafka Topic Setup
=========================================="
echo "Broker: $KAFKA_BROKER"
echo "Partitions: $PARTITIONS"
echo "Replication Factor: $REPLICATION_FACTOR"
echo ""

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
sleep 10

# Function to create topic
create_topic() {
    local topic_name=$1
    local retention_ms=${2:-604800000} # Default 7 days
    local cleanup_policy=${3:-delete}

    echo "Creating topic: $topic_name (retention: $retention_ms ms, cleanup: $cleanup_policy)"

    docker exec cdn-kafka-1 /opt/kafka/bin/kafka-topics.sh \
        --create \
        --bootstrap-server $KAFKA_BROKER \
        --topic $topic_name \
        --partitions $PARTITIONS \
        --replication-factor $REPLICATION_FACTOR \
        --config retention.ms=$retention_ms \
        --config cleanup.policy=$cleanup_policy \
        --config min.insync.replicas=2 \
        --if-not-exists
}

echo ""
echo "==========================================
Creating Video Upload Topics
=========================================="

# 7-day retention
create_topic "video.upload.initiated" 604800000
create_topic "video.upload.progress" 604800000
create_topic "video.upload.completed" 604800000
create_topic "video.upload.failed" 604800000
create_topic "video.upload.cancelled" 604800000

echo ""
echo "==========================================
Creating Video Processing Topics
=========================================="

# 7-day retention
create_topic "video.processing.queued" 604800000
create_topic "video.processing.started" 604800000
create_topic "video.processing.progress" 604800000
create_topic "video.processing.completed" 604800000
create_topic "video.processing.failed" 604800000
create_topic "video.processing.retry" 604800000

echo ""
echo "==========================================
Creating Job Management Topics
=========================================="

# 7-day retention
create_topic "job.created" 604800000
create_topic "job.assigned" 604800000
create_topic "job.started" 604800000
create_topic "job.progress" 604800000
create_topic "job.completed" 604800000
create_topic "job.failed" 604800000
create_topic "job.retry" 604800000
create_topic "job.cancelled" 604800000
create_topic "job.dlq" 2592000000 # 30 days for DLQ

echo ""
echo "==========================================
Creating Worker Health Topics
=========================================="

# 7-day retention
create_topic "worker.health.heartbeat" 604800000
create_topic "worker.health.status" 604800000
create_topic "worker.health.metrics" 604800000

echo ""
echo "==========================================
Creating Video Metadata Topics
=========================================="

# 7-day retention
create_topic "video.metadata.created" 604800000
create_topic "video.metadata.updated" 604800000
create_topic "video.metadata.deleted" 604800000
create_topic "video.published" 604800000
create_topic "video.unpublished" 604800000
create_topic "video.scheduled" 604800000
create_topic "video.status.changed" 604800000

echo ""
echo "==========================================
Creating Playback & Streaming Topics
=========================================="

# 30-day retention for analytics
create_topic "video.playback.started" 2592000000
create_topic "video.playback.progress" 2592000000
create_topic "video.playback.paused" 2592000000
create_topic "video.playback.resumed" 2592000000
create_topic "video.playback.quality_changed" 2592000000
create_topic "video.playback.buffering" 2592000000
create_topic "video.playback.error" 2592000000
create_topic "video.playback.completed" 2592000000

echo ""
echo "==========================================
Creating DRM Topics
=========================================="

# 90-day retention for audit
create_topic "drm.license.requested" 7776000000
create_topic "drm.license.issued" 7776000000
create_topic "drm.license.revoked" 7776000000
create_topic "drm.license.expired" 7776000000
create_topic "drm.device.registered" 7776000000
create_topic "drm.device.deregistered" 7776000000
create_topic "drm.concurrent.limit_exceeded" 7776000000

echo ""
echo "==========================================
Creating User & Portal Topics
=========================================="

# 30-day retention
create_topic "user.registered" 2592000000
create_topic "user.login" 2592000000
create_topic "user.logout" 2592000000
create_topic "user.profile.updated" 2592000000
create_topic "user.video.liked" 2592000000
create_topic "user.video.disliked" 2592000000
create_topic "user.video.commented" 2592000000
create_topic "user.video.reported" 2592000000
create_topic "user.subscription.created" 2592000000
create_topic "user.subscription.renewed" 2592000000
create_topic "user.subscription.cancelled" 2592000000
create_topic "user.favorite.added" 2592000000
create_topic "user.favorite.removed" 2592000000

echo ""
echo "==========================================
Creating CMS & Moderation Topics
=========================================="

# 30-day retention
create_topic "cms.video.moderation.submitted" 2592000000
create_topic "cms.video.moderation.approved" 2592000000
create_topic "cms.video.moderation.rejected" 2592000000
create_topic "cms.playlist.created" 2592000000
create_topic "cms.playlist.updated" 2592000000
create_topic "cms.playlist.deleted" 2592000000
create_topic "cms.user.activity" 2592000000

echo ""
echo "==========================================
Creating Analytics Topics
=========================================="

# 30-day retention
create_topic "analytics.video.stats" 2592000000
create_topic "analytics.user.engagement" 2592000000
create_topic "analytics.cdn.performance" 2592000000
create_topic "analytics.geographic.stats" 2592000000

echo ""
echo "==========================================
Creating System & Monitoring Topics
=========================================="

# 7-day retention
create_topic "system.health.check" 604800000
create_topic "system.alert.created" 604800000
create_topic "system.error" 604800000

echo ""
echo "==========================================
Listing All Topics
=========================================="

docker exec cdn-kafka-1 /opt/kafka/bin/kafka-topics.sh \
    --list \
    --bootstrap-server $KAFKA_BROKER

echo ""
echo "==========================================
Topic Details
=========================================="

docker exec cdn-kafka-1 /opt/kafka/bin/kafka-topics.sh \
    --describe \
    --bootstrap-server $KAFKA_BROKER

echo ""
echo "✅ Kafka topic setup complete!"
echo "Total topics created: 70+"
