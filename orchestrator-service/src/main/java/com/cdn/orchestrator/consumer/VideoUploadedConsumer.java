package com.cdn.orchestrator.consumer;

import com.cdn.orchestrator.service.VideoOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class VideoUploadedConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoOrchestrationService orchestrationService;

    @Value("${orchestrator.streams.trigger}")
    private String streamName;

    private String lastId = "0-0";

    @Scheduled(fixedDelay = 2000) // Poll every 2 seconds
    public void pollStream() {
        try {
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(StreamOffset.create(streamName, ReadOffset.from(lastId)));

            if (messages != null && !messages.isEmpty()) {
                for (MapRecord<String, Object, Object> message : messages) {
                    processMessage(message);
                    lastId = message.getId().getValue();
                }
            }
        } catch (Exception e) {
            log.error("Error polling video uploaded stream: {}", e.getMessage());
        }
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        try {
            Object videoIdObj = message.getValue().get("videoId");
            String videoId = videoIdObj != null ? videoIdObj.toString() : null;

            if (videoId == null) {
                log.warn("Received message without videoId");
                return;
            }

            log.info("📥 Received video uploaded event: {}", videoId);
            orchestrationService.orchestrateVideo(UUID.fromString(videoId));

        } catch (Exception e) {
            log.error("Failed to process upload event: {}", e.getMessage(), e);
        }
    }
}
