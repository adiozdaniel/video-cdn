package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaCompletionEventConsumer {

    private final CompletionTrackingService completionTrackingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "processing-jobs-completed",
        groupId = "orchestrator-group",
        concurrency = "3"
    )
    public void consumeCompletionEvent(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            // Convert Map to CompletionEvent
            CompletionEvent event = objectMapper.convertValue(payload, CompletionEvent.class);

            log.info("Received completion event: videoId={}, profile={}, status={}, partition={}, offset={}",
                event.getVideoId(), event.getProfile(), event.getStatus(), partition, offset);

            // Process completion event
            completionTrackingService.handleProfileCompleted(event);

            log.debug("Successfully processed completion event for {} {}",
                event.getVideoId(), event.getProfile());

        } catch (Exception e) {
            log.error("Error processing completion event from partition {} offset {}: {}",
                partition, offset, e.getMessage(), e);
            // Don't throw exception - let Kafka commit the offset and continue
            // Failed events will need manual investigation
        }
    }
}
