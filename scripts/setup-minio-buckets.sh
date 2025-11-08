#!/bin/bash

# ==========================================
# MinIO Bucket Initialization Script
# Creates all required buckets for CDN platform
# ==========================================

set -e

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin123}"
MC_ALIAS="${MC_ALIAS:-cdnminio}"

echo "==========================================
MinIO Bucket Setup
=========================================="
echo "Endpoint: $MINIO_ENDPOINT"
echo "Alias: $MC_ALIAS"
echo ""

# Check if mc (MinIO Client) is installed
if ! command -v mc &> /dev/null; then
    echo "MinIO Client (mc) not found. Installing..."
    wget https://dl.min.io/client/mc/release/linux-amd64/mc -O /tmp/mc
    chmod +x /tmp/mc
    MC_CMD="/tmp/mc"
else
    MC_CMD="mc"
fi

# Configure MinIO alias
echo "Configuring MinIO alias..."
$MC_CMD alias set $MC_ALIAS $MINIO_ENDPOINT $MINIO_ACCESS_KEY $MINIO_SECRET_KEY

echo ""
echo "==========================================
Creating Buckets
=========================================="

# Function to create bucket with versioning and lifecycle policy
create_bucket() {
    local bucket_name=$1
    local versioning=${2:-off}
    local lifecycle_days=${3:-0}

    echo "Creating bucket: $bucket_name"
    $MC_CMD mb $MC_ALIAS/$bucket_name --ignore-existing

    if [ "$versioning" == "on" ]; then
        echo "  ✓ Enabling versioning for $bucket_name"
        $MC_CMD version enable $MC_ALIAS/$bucket_name
    fi

    if [ "$lifecycle_days" -gt 0 ]; then
        echo "  ✓ Setting lifecycle policy: delete after $lifecycle_days days"
        cat > /tmp/${bucket_name}_lifecycle.json <<EOF
{
    "Rules": [
        {
            "ID": "expire-old-objects",
            "Status": "Enabled",
            "Expiration": {
                "Days": $lifecycle_days
            }
        }
    ]
}
EOF
        $MC_CMD ilm import $MC_ALIAS/$bucket_name < /tmp/${bucket_name}_lifecycle.json
        rm /tmp/${bucket_name}_lifecycle.json
    fi

    echo "  ✅ Bucket $bucket_name created successfully"
    echo ""
}

# Create video storage buckets
echo "--- Video Storage Buckets ---"
create_bucket "videos-raw" "on" 0  # Keep forever, versioned
create_bucket "videos-processed" "off" 0  # Keep forever
create_bucket "videos-thumbnails" "off" 0  # Keep forever
create_bucket "videos-subtitles" "off" 0  # Keep forever

echo "--- Temporary & Cache Buckets ---"
create_bucket "videos-temp" "off" 7  # Delete after 7 days
create_bucket "videos-preview" "off" 30  # Delete after 30 days

echo "--- User Content Buckets ---"
create_bucket "user-avatars" "off" 0
create_bucket "user-uploads" "on" 90  # Keep for 90 days, versioned

echo "--- CMS Content Buckets ---"
create_bucket "cms-assets" "off" 0  # Images, banners, etc.
create_bucket "cms-exports" "off" 30  # Export files, auto-delete

echo ""
echo "==========================================
Listing All Buckets
=========================================="

$MC_CMD ls $MC_ALIAS

echo ""
echo "==========================================
Bucket Information
=========================================="

for bucket in videos-raw videos-processed videos-thumbnails videos-subtitles videos-temp videos-preview user-avatars user-uploads cms-assets cms-exports; do
    echo "--- Bucket: $bucket ---"
    $MC_CMD stat $MC_ALIAS/$bucket
    echo ""
done

echo "==========================================
Setting Public Read Policy for Processed Content
=========================================="

# Set public read policy for processed videos and thumbnails
for bucket in videos-processed videos-thumbnails; do
    echo "Setting public read for: $bucket"
    cat > /tmp/${bucket}_policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": ["*"]
            },
            "Action": [
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::${bucket}/*"
            ]
        }
    ]
}
EOF
    $MC_CMD anonymous set-json /tmp/${bucket}_policy.json $MC_ALIAS/$bucket
    rm /tmp/${bucket}_policy.json
    echo "  ✅ Public read enabled for $bucket"
    echo ""
done

echo ""
echo "✅ MinIO bucket setup complete!"
echo "Total buckets created: 10"
