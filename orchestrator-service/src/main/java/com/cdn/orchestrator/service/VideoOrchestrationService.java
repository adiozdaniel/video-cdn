package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import com.cdn.orchestrator.dto.VideoChunk;
import com.cdn.orchestrator.dto.VideoMetadata;
import com.cdn.orchestrator.model.Video;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.model.VideoChunkJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoChunkJobRepository;
import com.cdn.orchestrator.repository.VideoRepository;
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
    private final VideoChunkJobRepository chunkJobRepository;
    private final VideoProbeService probeService;
    private final ProfileSelectorService profileSelector;
    private final KafkaProfileJobProducer kafkaProducer;
    private final MinioService minioService;
    private final VideoChunkingService chunkingService;

    @Transactional
    public void orchestrateVideo(UUID videoId) {
        log.info("🎬 Starting orchestration for video: {}", videoId);

        // 1. Get video from database
        Video video = videoRepository.findById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        // 2. Set status to PROCESSING
        video.setStatus("PROCESSING");
        videoRepository.save(video);
        log.info("Set video {} status to PROCESSING", videoId);

        try {
            // 3. Download video temporarily for probing
            File videoFile = minioService.downloadForProbing(videoId, video.getFilename());

            // 3. Probe video metadata
            VideoMetadata metadata = probeService.probeVideo(videoFile);

            // 4. Select profiles adaptively
            List<String> profiles = profileSelector.selectProfiles(metadata);
            String preset = profileSelector.selectPreset(metadata.duration());

            log.info("Selected {} profiles for video {}: {}", profiles.size(), videoId, profiles);

            // 5. Phase 4: Check if chunking is needed
            boolean needsChunking = chunkingService.needsChunking(metadata.duration());
            List<VideoChunk> chunks = chunkingService.splitVideo(videoId, metadata.duration());

            if (needsChunking) {
                log.info("📦 Phase 4: Video {} will be processed with {} chunks", videoId, chunks.size());
                orchestrateWithChunking(videoId, video.getFilename(), profiles, preset, chunks);
            } else {
                log.info("🎥 Phase 3: Video {} will be processed as whole", videoId);
                orchestrateWholeVideo(videoId, video.getFilename(), profiles, preset);
            }

            // 6. Clean up temp file
            videoFile.delete();

            log.info("✅ Orchestration complete for video: {}", videoId);

        } catch (Exception e) {
            log.error("❌ Orchestration failed for video {}: {}", videoId, e.getMessage(), e);
            video.setStatus("FAILED");
            videoRepository.save(video);
        }
    }

    /**
     * Phase 3: Orchestrate whole video processing (no chunking)
     */
    private void orchestrateWholeVideo(UUID videoId, String filename,
                                       List<String> profiles, String preset) {
        // Create profile job records
        for (String profile : profiles) {
            VideoProfileJob job = new VideoProfileJob(videoId, profile);
            profileJobRepository.save(job);
        }

        // Publish 480p FIRST (high priority)
        if (profiles.contains("480p")) {
            publishJob("480p", videoId, filename, "ultrafast");
        }

        // Publish other profiles
        for (String profile : profiles) {
            if (!profile.equals("480p")) {
                publishJob(profile, videoId, filename, preset);
            }
        }
    }

    /**
     * Phase 4: Orchestrate chunked video processing
     */
    private void orchestrateWithChunking(UUID videoId, String filename,
                                         List<String> profiles, String preset,
                                         List<VideoChunk> chunks) {
        // Create chunk job records for each profile and chunk
        for (String profile : profiles) {
            for (VideoChunk chunk : chunks) {
                VideoChunkJob job = new VideoChunkJob(
                    videoId,
                    profile,
                    chunk.getChunkId(),
                    chunk.getStartTime(),
                    chunk.getEndTime()
                );
                chunkJobRepository.save(job);
            }
        }

        // Publish 480p chunks FIRST (high priority for fast PLAYABLE state)
        if (profiles.contains("480p")) {
            for (VideoChunk chunk : chunks) {
                publishChunkJob("480p", videoId, filename, "ultrafast", chunk);
            }
        }

        // Publish other profile chunks
        for (String profile : profiles) {
            if (!profile.equals("480p")) {
                for (VideoChunk chunk : chunks) {
                    publishChunkJob(profile, videoId, filename, preset, chunk);
                }
            }
        }

        log.info("Published {} chunk jobs for video {} ({} profiles × {} chunks)",
            profiles.size() * chunks.size(), videoId, profiles.size(), chunks.size());
    }

    private void publishJob(String profile, UUID videoId, String filename, String preset) {
        ProfileJobMessage job = new ProfileJobMessage(videoId, profile, filename, preset);
        kafkaProducer.publishProfileJob(profile, job);
    }

    private void publishChunkJob(String profile, UUID videoId,
                                 String filename, String preset, VideoChunk chunk) {
        ProfileJobMessage job = new ProfileJobMessage(
            videoId,
            profile,
            filename,
            preset,
            chunk.getChunkId(),
            chunk.getStartTime(),
            chunk.getEndTime()
        );
        kafkaProducer.publishProfileJob(profile, job);
    }
}
