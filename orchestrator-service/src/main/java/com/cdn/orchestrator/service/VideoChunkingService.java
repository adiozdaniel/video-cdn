package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.VideoChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VideoChunkingService {

    private static final double CHUNK_SIZE_SECONDS = 120.0; // 2 minutes

    @Value("${orchestrator.chunking.threshold-seconds:600}") // 10 minutes default
    private double chunkThresholdSeconds;

    @Value("${orchestrator.chunking.enabled:true}")
    private boolean chunkingEnabled;

    /**
     * Split video into chunks if duration exceeds threshold.
     * Returns single "chunk" (entire video) if below threshold or chunking disabled.
     *
     * @param videoId Video UUID
     * @param duration Video duration in seconds
     * @return List of VideoChunk objects
     */
    public List<VideoChunk> splitVideo(UUID videoId, double duration) {
        if (!chunkingEnabled || duration <= chunkThresholdSeconds) {
            log.info("Video {} ({:.2f}s) does not need chunking (threshold: {:.2f}s)",
                videoId, duration, chunkThresholdSeconds);
            // Return single "chunk" representing entire video (chunkId = 0)
            return List.of(new VideoChunk(videoId, 0, 0.0, duration));
        }

        List<VideoChunk> chunks = new ArrayList<>();
        int chunkCount = (int) Math.ceil(duration / CHUNK_SIZE_SECONDS);

        for (int i = 0; i < chunkCount; i++) {
            double startTime = i * CHUNK_SIZE_SECONDS;
            double endTime = Math.min((i + 1) * CHUNK_SIZE_SECONDS, duration);

            chunks.add(new VideoChunk(videoId, i, startTime, endTime));
        }

        log.info("Video {} ({:.2f}s) split into {} chunks of {:.2f}s each",
            videoId, duration, chunkCount, CHUNK_SIZE_SECONDS);

        return chunks;
    }

    /**
     * Check if video needs chunking based on duration
     */
    public boolean needsChunking(double duration) {
        return chunkingEnabled && duration > chunkThresholdSeconds;
    }
}
