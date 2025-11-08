# Runtime-only Dockerfile
# Expects pre-built binary from build script

FROM ubuntu:24.04

WORKDIR /app

# Install runtime dependencies (FFmpeg for video processing)
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Create temp directory for processing
RUN mkdir -p /tmp/processing && chmod 777 /tmp/processing

# Copy pre-built binary from build directory
COPY build/processing_worker /app/processing_worker

# Make executable
RUN chmod +x /app/processing_worker

# Set environment variables
ENV TEMP_DIR=/tmp/processing

# Run the binary
CMD ["/app/processing_worker"]
