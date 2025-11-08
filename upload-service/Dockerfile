# Runtime-only Dockerfile
# Expects pre-built binary from build script

FROM ubuntu:24.04

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

# Copy pre-built binary from build directory
COPY build/upload_service /app/upload_service

# Make executable
RUN chmod +x /app/upload_service

# Expose port
EXPOSE 8080

# Run the binary
CMD ["/app/upload_service"]
