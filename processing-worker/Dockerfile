# Multi-stage build for processing worker with FFmpeg
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache git

# Copy go mod files
COPY go.mod go.sum* ./
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-w -s" \
    -o processing-worker \
    ./cmd/worker

# Final stage - with FFmpeg
FROM alpine:latest

# Install FFmpeg and other dependencies
RUN apk add --no-cache \
    ffmpeg \
    ca-certificates

WORKDIR /root/

# Copy binary from builder
COPY --from=builder /app/processing-worker .

# Create temp directory for video processing
RUN mkdir -p /tmp/processing

# Run the worker
CMD ["./processing-worker"]
