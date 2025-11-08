# Build stage
FROM rust:latest as builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy manifests
COPY Cargo.toml Cargo.lock ./

# Copy source code
COPY src ./src

# Build release binary
RUN cargo build --release

# Runtime stage
FROM debian:bookworm-slim

WORKDIR /app

# Install runtime dependencies including FFmpeg
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    ffmpeg \
    ffprobe \
    && rm -rf /var/lib/apt/lists/*

# Create temp directory for processing
RUN mkdir -p /tmp/processing && chmod 777 /tmp/processing

# Copy the binary from builder
COPY --from=builder /app/target/release/processing_worker /app/processing_worker

# Set environment variables
ENV TEMP_DIR=/tmp/processing

# Run the binary
CMD ["/app/processing_worker"]
