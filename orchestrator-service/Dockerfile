FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install ffmpeg for video probing
RUN apk add --no-cache ffmpeg wget

# Copy pre-built JAR
COPY orchestrator-service/target/orchestrator-service.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
