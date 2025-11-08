# Runtime-only Dockerfile
# Expects pre-built JAR from build script

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy pre-built JAR from build directory
COPY build/video-service.jar /app/video-service.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://127.0.0.1:8081/actuator/health || exit 1

# Run the JAR
ENTRYPOINT ["java", "-jar", "/app/video-service.jar"]
