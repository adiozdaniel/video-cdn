#!/bin/bash
# Setup MinIO bucket permissions for HLS streaming

echo "Configuring MinIO bucket permissions..."

docker exec cdn-minio sh -c '
# Configure MC alias
mc alias set myminio http://localhost:9000 minioadmin minioadmin123

# Set public download access for HLS files
mc anonymous set download myminio/videos/hls

echo "✅ MinIO bucket permissions configured successfully"
'
