package com.cdn.orchestrator.consumer;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.cdn.orchestrator.service.CompletionTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CompletionEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CompletionTrackingService trackingService;
    private final ObjectMapper objectMapper;

    @Value("${orchestrator.streams.completed}")
    private String streamName;

    private String lastId = "0-0";

    @Scheduled(fixedDelay = 1000) // Poll every 1 second
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
            log.error("Error polling completion stream: {}", e.getMessage());
        }
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> data = (Map<Object, Object>) message.getValue();

            CompletionEvent event = objectMapper.convertValue(data, CompletionEvent.class);

            log.info("✅ Received completion event: {} - {}", event.getVideoId(), event.getProfile());
            trackingService.handleProfileCompleted(event);

        } catch (Exception e) {
            log.error("Failed to process completion event: {}", e.getMessage(), e);
        }
    }
}
