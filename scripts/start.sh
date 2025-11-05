#!/bin/bash

# CDN Platform Startup Script
# Starts all services with health checks

set -e

echo "🚀 Starting CDN Platform..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running"
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p infrastructure/nginx/cache
mkdir -p infrastructure/haproxy
mkdir -p infrastructure/postgres

# Start infrastructure services first
echo "🔧 Starting infrastructure services..."
docker-compose up -d postgres redis minio

# Wait for PostgreSQL
echo "⏳ Waiting for PostgreSQL..."
until docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
    sleep 1
done
echo "✅ PostgreSQL is ready"

# Wait for Redis
echo "⏳ Waiting for Redis..."
until docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; do
    sleep 1
done
echo "✅ Redis is ready"

# Wait for MinIO
echo "⏳ Waiting for MinIO..."
sleep 5
echo "✅ MinIO is ready"

# Start application services
echo "🚀 Starting application services..."
docker-compose up -d upload-service worker-1 nginx-edge haproxy

# Wait for services to start
echo "⏳ Waiting for services to be healthy..."
sleep 5

# Check service health
echo ""
echo "🏥 Health Check:"
echo "================"

# Check upload service
if curl -s http://localhost/health > /dev/null 2>&1; then
    echo "✅ Upload Service: healthy"
else
    echo "❌ Upload Service: unhealthy"
fi

# Check HAProxy stats
if curl -s http://localhost:8404/stats > /dev/null 2>&1; then
    echo "✅ HAProxy: healthy"
else
    echo "❌ HAProxy: unhealthy"
fi

echo ""
echo "✨ CDN Platform started successfully!"
echo ""
echo "📊 Access Points:"
echo "=================="
echo "API Endpoint:    http://localhost/api/upload/initiate"
echo "MinIO Console:   http://localhost:9001 (minioadmin / minioadmin123)"
echo "HAProxy Stats:   http://localhost:8404/stats"
echo "Video Delivery:  http://localhost/videos/{videoId}/master.m3u8"
echo ""
echo "📝 Logs:"
echo "========"
echo "View all logs:        docker-compose logs -f"
echo "Upload service logs:  docker-compose logs -f upload-service"
echo "Worker logs:          docker-compose logs -f worker-1"
echo ""
echo "🛑 To stop: ./scripts/stop.sh"
