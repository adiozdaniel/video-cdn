package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProfileJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Mono<Void> publishProfileJob(String profile, ProfileJobMessage job) {
        String topicName = "processing-jobs-" + profile;

        return Mono.fromFuture(kafkaTemplate.send(topicName, job.getVideoId(), job))
            .doOnSuccess(result -> log.info("Published {} job to {}: videoId={}, partition={}, offset={}",
                profile, topicName, job.getVideoId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset()))
            .doOnError(ex -> log.error("Failed to publish {} job to {}: {}",
                profile, topicName, ex.getMessage()))
            .then();
    }
}
