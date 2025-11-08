#!/bin/sh
# Wait for MinIO to be ready
sleep 5

# Install mc (MinIO client) if not present
if ! command -v mc &> /dev/null; then
    wget https://dl.min.io/client/mc/release/linux-amd64/mc -O /usr/local/bin/mc
    chmod +x /usr/local/bin/mc
fi

# Configure mc alias
mc alias set myminio http://minio:9000 minioadmin minioadmin123

# Set CORS policy for the bucket
mc anonymous set-json /tmp/cors-policy.json myminio/videos

echo "MinIO CORS configured successfully"
