package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

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
            Map<String, Object> jobMap = objectMapper.convertValue(job, Map.class);

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
