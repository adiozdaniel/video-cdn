package com.cdn.orchestrator.consumer;

import com.cdn.orchestrator.service.VideoOrchestrationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class VideoUploadedConsumer {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final VideoOrchestrationService orchestrationService;

    @Value("${orchestrator.streams.trigger}")
    private String streamName;

    private String lastId = "0-0";

    @PostConstruct
    public void init() {
        startPolling();
    }

    public void startPolling() {
        Flux.interval(Duration.ofSeconds(2))
            .flatMap(i -> pollStream())
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                null,
                e -> log.error("Error in video uploaded consumer: {}", e.getMessage())
            );
    }

    private Flux<Void> pollStream() {
        return redisTemplate.opsForStream()
            .read(StreamOffset.create(streamName, ReadOffset.from(lastId)))
            .flatMap(record -> {
                lastId = record.getId().getValue();
                return processMessage(record.getValue().get("videoId"));
            });
    }

    private Mono<Void> processMessage(Object videoIdObj) {
        if (videoIdObj == null) {
            log.warn("Received message without videoId");
            return Mono.empty();
        }

        String videoId = videoIdObj.toString();
        log.info("📥 Received video uploaded event: {}", videoId);

        return orchestrationService.orchestrateVideo(UUID.fromString(videoId))
            .onErrorResume(e -> {
                log.error("Failed to process upload event for video {}: {}", videoId, e.getMessage());
                return Mono.empty();
            });
    }
}
