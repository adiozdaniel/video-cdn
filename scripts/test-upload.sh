#!/bin/bash

# Test video upload workflow

set -e

echo "🧪 Testing CDN Video Upload Workflow"
echo "===================================="
echo ""

# Check if test video file provided
if [ -z "$1" ]; then
    echo "Usage: ./scripts/test-upload.sh <video-file.mp4>"
    echo ""
    echo "Example: ./scripts/test-upload.sh sample.mp4"
    exit 1
fi

VIDEO_FILE="$1"

if [ ! -f "$VIDEO_FILE" ]; then
    echo "❌ Error: Video file not found: $VIDEO_FILE"
    exit 1
fi

# Get file size
FILE_SIZE=$(stat -f%z "$VIDEO_FILE" 2>/dev/null || stat -c%s "$VIDEO_FILE" 2>/dev/null)
FILENAME=$(basename "$VIDEO_FILE")

echo "📹 Video: $FILENAME"
echo "📏 Size: $(numfmt --to=iec-i --suffix=B $FILE_SIZE 2>/dev/null || echo $FILE_SIZE bytes)"
echo ""

# Step 1: Get presigned upload URL
echo "1️⃣ Requesting upload URL..."
RESPONSE=$(curl -s -X POST http://localhost/api/upload/initiate \
    -H "Content-Type: application/json" \
    -d "{\"filename\": \"$FILENAME\", \"size\": $FILE_SIZE}")

VIDEO_ID=$(echo "$RESPONSE" | grep -o '"videoId":"[^"]*"' | cut -d'"' -f4)
UPLOAD_URL=$(echo "$RESPONSE" | grep -o '"uploadUrl":"[^"]*"' | cut -d'"' -f4)

if [ -z "$VIDEO_ID" ]; then
    echo "❌ Failed to get upload URL"
    echo "Response: $RESPONSE"
    exit 1
fi

echo "✅ Video ID: $VIDEO_ID"
echo ""

# Step 2: Upload video to MinIO
echo "2️⃣ Uploading video..."
curl -X PUT "$UPLOAD_URL" \
    --upload-file "$VIDEO_FILE" \
    --progress-bar -o /dev/null

echo "✅ Upload complete"
echo ""

# Step 3: Mark upload as complete
echo "3️⃣ Triggering processing..."
curl -s -X POST "http://localhost/api/upload/$VIDEO_ID/complete" > /dev/null
echo "✅ Processing queued"
echo ""

# Step 4: Monitor processing status
echo "4️⃣ Monitoring processing status..."
echo "(This may take several minutes depending on video length)"
echo ""

while true; do
    STATUS_RESPONSE=$(curl -s "http://localhost/api/upload/$VIDEO_ID/status")
    STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    echo -n "   Status: $STATUS"

    if [ "$STATUS" = "READY" ]; then
        echo " ✅"
        echo ""
        break
    elif [ "$STATUS" = "FAILED" ]; then
        echo " ❌"
        echo ""
        echo "Processing failed. Check worker logs:"
        echo "docker-compose logs worker-1"
        exit 1
    else
        echo " ⏳"
        sleep 5
    fi
done

# Step 5: Get playback URL
PLAYBACK_URL="http://localhost/videos/processed/$VIDEO_ID/master.m3u8"

echo "🎉 Video ready!"
echo ""
echo "📺 Playback URL:"
echo "   $PLAYBACK_URL"
echo ""
echo "🎬 Test playback with curl:"
echo "   curl -I $PLAYBACK_URL"
echo ""
echo "🌐 Test in browser with HLS.js:"
cat <<EOF
<!DOCTYPE html>
<html>
<head>
    <title>Test Video: $VIDEO_ID</title>
</head>
<body>
    <video id="video" controls width="720"></video>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script>
        const video = document.getElementById('video');
        if (Hls.isSupported()) {
            const hls = new Hls();
            hls.loadSource('$PLAYBACK_URL');
            hls.attachMedia(video);
        }
    </script>
</body>
</html>
EOF
