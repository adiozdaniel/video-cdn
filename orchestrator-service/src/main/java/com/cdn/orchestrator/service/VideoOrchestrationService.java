package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.ProfileJobMessage;
import com.cdn.orchestrator.dto.VideoMetadata;
import com.cdn.orchestrator.model.Video;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
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
            log.error("❌ Orchestration failed for video {}: {}", videoId, e.getMessage(), e);
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
