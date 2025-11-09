package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProfileJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProfileJob(String profile, ProfileJobMessage job) {
        String topicName = "processing-jobs-" + profile;

        try {
            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topicName, job.getVideoId(), job);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} job to {}: {}",
                        profile, topicName, ex.getMessage());
                } else {
                    log.info("Published {} job to {}: videoId={}, partition={}, offset={}",
                        profile, topicName, job.getVideoId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("Failed to publish job to {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Failed to publish job", e);
        }
    }
}
