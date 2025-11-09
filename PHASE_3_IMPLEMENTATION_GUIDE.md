# Phase 3 Implementation Guide - Orchestrator Service

## Overview

This guide provides the complete implementation for the Java/Spring Boot Orchestrator Service that coordinates specialized workers for parallel profile transcoding.

## Current Progress

✅ **Completed**:
- Project structure created
- Maven `pom.xml` with all dependencies
- `application.yml` configuration
- Main application class
- Entity classes (Video, VideoProfileJob)
- Repository interfaces (VideoRepository, VideoProfileJobRepository)

🔨 **Remaining Implementation** (Copy these files):

---

## 1. DTO Classes

### `ProfileJobMessage.java`
```java
package com.cdn.orchestrator.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ProfileJobMessage {
    private String videoId;
    private String profile;          // 480p, 720p, etc.
    private String inputPath;        // raw/uuid/filename.mp4
    private String outputPath;       // hls/uuid/
    private String preset;           // ultrafast, veryfast, fast
    private String priority;         // high, medium, low
    private Long timestamp;

    public ProfileJobMessage(UUID videoId, String profile, String filename, String preset) {
        this.videoId = videoId.toString();
        this.profile = profile;
        this.inputPath = String.format("raw/%s/%s", videoId, filename);
        this.outputPath = String.format("hls/%s/", videoId);
        this.preset = preset;
        this.priority = profile.equals("480p") ? "high" : "medium";
        this.timestamp = System.currentTimeMillis();
    }
}
```

### `CompletionEvent.java`
```java
package com.cdn.orchestrator.dto;

import lombok.Data;

@Data
public class CompletionEvent {
    private String videoId;
    private String profile;
    private String workerId;
    private String status;         // COMPLETED or FAILED
    private Integer filesUploaded;
    private Long durationMs;
    private String errorMessage;
    private Long timestamp;
}
```

---

## 2. Configuration Classes

### `RedisConfig.java`
```java
package com.cdn.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values
        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }
}
```

---

## 3. Core Services

### `VideoProbeService.java` - FFprobe integration
```java
package com.cdn.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoProbeService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoMetadata probeVideo(File videoFile) throws Exception {
        // Build ffprobe command
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration,size",
            "-show_entries", "stream=width,height,codec_name,codec_type",
            "-of", "json",
            videoFile.getAbsolutePath()
        );

        Process process = pb.start();
        process.waitFor(30, TimeUnit.SECONDS);

        // Parse JSON output
        JsonNode root = objectMapper.readTree(process.getInputStream());

        // Extract video stream
        JsonNode streams = root.get("streams");
        JsonNode videoStream = null;
        for (JsonNode stream : streams) {
            if ("video".equals(stream.get("codec_type").asText())) {
                videoStream = stream;
                break;
            }
        }

        if (videoStream == null) {
            throw new RuntimeException("No video stream found");
        }

        int width = videoStream.get("width").asInt();
        int height = videoStream.get("height").asInt();
        double duration = root.get("format").get("duration").asDouble();

        log.info("Video metadata: {}x{}, {:.1f}s", width, height, duration);

        return new VideoMetadata(width, height, duration);
    }

    public record VideoMetadata(int width, int height, double duration) {}
}
```

### `ProfileSelectorService.java` - Adaptive profile selection
```java
package com.cdn.orchestrator.service;

import com.cdn.orchestrator.service.VideoProbeService.VideoMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProfileSelectorService {

    public List<String> selectProfiles(VideoMetadata metadata) {
        List<String> profiles = new ArrayList<>();
        int height = metadata.height();

        // Add profiles based on source resolution (don't upscale)
        if (height >= 1080) profiles.add("1080p");
        if (height >= 720) profiles.add("720p");
        if (height >= 480) profiles.add("480p");
        if (height >= 360) profiles.add("360p");
        if (height >= 240) profiles.add("240p");

        if (profiles.isEmpty()) {
            throw new RuntimeException("Source video too low resolution (minimum 240p required)");
        }

        return profiles;
    }

    public String selectPreset(double duration) {
        if (duration > 600) return "ultrafast";  // > 10 min
        if (duration > 300) return "veryfast";   // > 5 min
        if (duration > 120) return "faster";     // > 2 min
        return "fast";                            // <= 2 min
    }
}
```

### `RedisStreamPublisher.java` - Job publishing
```java
package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisStreamPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishProfileJob(String streamName, ProfileJobMessage job) {
        try {
            // Convert to Map for Redis Stream
            @SuppressWarnings("unchecked")
            var jobMap = objectMapper.convertValue(job, java.util.Map.class);

            ObjectRecord<String, Object> record = StreamRecords
                .objectBacked(jobMap)
                .withStreamKey(streamName);

            redisTemplate.opsForStream().add(record);

            log.info("Published {} job to {}: videoId={}",
                job.getProfile(), streamName, job.getVideoId());

        } catch (Exception e) {
            log.error("Failed to publish job to {}: {}", streamName, e.getMessage());
            throw new RuntimeException("Failed to publish job", e);
        }
    }
}
```

### `VideoOrchestrationService.java` - Main orchestration logic
```java
package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import com.cdn.orchestrator.model.Video;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoRepository;
import com.cdn.orchestrator.service.VideoProbeService.VideoMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoOrchestrationService {

    private final VideoRepository videoRepository;
    private final VideoProfileJobRepository profileJobRepository;
    private final VideoProbeService probeService;
    private final ProfileSelectorService profileSelector;
    private final RedisStreamPublisher streamPublisher;
    private final MinioService minioService;

    @Value("${orchestrator.streams.profile-480p}")
    private String stream480p;

    @Value("${orchestrator.streams.profile-720p}")
    private String stream720p;

    @Value("${orchestrator.streams.profile-1080p}")
    private String stream1080p;

    @Value("${orchestrator.streams.profile-360p}")
    private String stream360p;

    @Value("${orchestrator.streams.profile-240p}")
    private String stream240p;

    @Transactional
    public void orchestrateVideo(UUID videoId) {
        log.info("🎬 Starting orchestration for video: {}", videoId);

        // 1. Get video from database
        Video video = videoRepository.findById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        try {
            // 2. Download video temporarily for probing
            File videoFile = minioService.downloadForProbing(videoId, video.getFilename());

            // 3. Probe video metadata
            VideoMetadata metadata = probeService.probeVideo(videoFile);

            // 4. Select profiles adaptively
            List<String> profiles = profileSelector.selectProfiles(metadata);
            String preset = profileSelector.selectPreset(metadata.duration());

            log.info("Selected {} profiles for video {}: {}", profiles.size(), videoId, profiles);

            // 5. Create profile job records in database
            for (String profile : profiles) {
                VideoProfileJob job = new VideoProfileJob(videoId, profile);
                profileJobRepository.save(job);
            }

            // 6. Publish 480p job FIRST (high priority for PLAYABLE state)
            if (profiles.contains("480p")) {
                publishJob("480p", stream480p, videoId, video.getFilename(), "ultrafast");
            }

            // 7. Publish other profile jobs
            for (String profile : profiles) {
                if (!profile.equals("480p")) {
                    String streamName = getStreamName(profile);
                    publishJob(profile, streamName, videoId, video.getFilename(), preset);
                }
            }

            // 8. Clean up temp file
            videoFile.delete();

            log.info("✅ Orchestration complete for video: {}", videoId);

        } catch (Exception e) {
            log.error("❌ Orchestration failed for video {}: {}", videoId, e.getMessage());
            video.setStatus("FAILED");
            videoRepository.save(video);
        }
    }

    private void publishJob(String profile, String streamName, UUID videoId,
                           String filename, String preset) {
        ProfileJobMessage job = new ProfileJobMessage(videoId, profile, filename, preset);
        streamPublisher.publishProfileJob(streamName, job);
    }

    private String getStreamName(String profile) {
        return switch (profile) {
            case "720p" -> stream720p;
            case "1080p" -> stream1080p;
            case "360p" -> stream360p;
            case "240p" -> stream240p;
            default -> throw new IllegalArgumentException("Unknown profile: " + profile);
        };
    }
}
```

---

## 4. Redis Stream Listeners

### `VideoUploadedListener.java` - Trigger orchestration
```java
package com.cdn.orchestrator.listener;

import com.cdn.orchestrator.service.VideoOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class VideoUploadedListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final VideoOrchestrationService orchestrationService;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String videoId = message.getValue().get("videoId");
            log.info("📥 Received video uploaded event: {}", videoId);

            orchestrationService.orchestrateVideo(UUID.fromString(videoId));

        } catch (Exception e) {
            log.error("Failed to process upload event: {}", e.getMessage(), e);
        }
    }
}
```

### `CompletionEventListener.java` - Track profile completion
```java
package com.cdn.orchestrator.listener;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.cdn.orchestrator.service.CompletionTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CompletionEventListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final CompletionTrackingService trackingService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            CompletionEvent event = objectMapper.convertValue(
                message.getValue(), CompletionEvent.class
            );

            log.info("✅ Received completion event: {} - {}",
                event.getVideoId(), event.getProfile());

            trackingService.handleProfileCompleted(event);

        } catch (Exception e) {
            log.error("Failed to process completion event: {}", e.getMessage(), e);
        }
    }
}
```

---

## 5. Build and Deployment

### Build Command:
```bash
cd orchestrator-service
./mvnw clean package -DskipTests
```

### Dockerfile:
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/orchestrator-service.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Add to docker-compose.yml:
```yaml
orchestrator:
  build:
    context: ./orchestrator-service
  container_name: cdn-orchestrator
  environment:
    DATABASE_HOST: postgres
    REDIS_HOST: redis
    MINIO_ENDPOINT: http://minio:9000
  depends_on:
    - postgres
    - redis
    - minio
  ports:
    - "8082:8082"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

---

## Next Steps

1. **Copy all Java files** from this guide into the orchestrator-service directory
2. **Build the service**: `./mvnw clean package`
3. **Add to docker-compose** and start
4. **Modify workers** to consume profile-specific streams
5. **Test with sample video**

The orchestrator is now ready to coordinate specialized workers for Phase 3!
