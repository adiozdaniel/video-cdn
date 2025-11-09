package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompletionTrackingService {

    private final VideoProfileJobRepository profileJobRepository;
    private final VideoRepository videoRepository;
    private final PlaylistGeneratorService playlistGenerator;

    @Transactional
    public void handleProfileCompleted(CompletionEvent event) {
        UUID videoId = UUID.fromString(event.getVideoId());
        String profile = event.getProfile();

        log.info("Handling completion event for video {} profile {}", videoId, profile);

        // Update profile job status
        List<VideoProfileJob> jobs = profileJobRepository.findByVideoId(videoId);
        VideoProfileJob job = jobs.stream()
            .filter(j -> j.getProfile().equals(profile))
            .findFirst()
            .orElse(null);

        if (job == null) {
            log.warn("Profile job not found: {} - {}", videoId, profile);
            return;
        }

        job.setStatus(event.getStatus());
        job.setCompletedAt(LocalDateTime.now());
        job.setDurationMs(event.getDurationMs() != null ? event.getDurationMs().intValue() : null);
        job.setFilesCount(event.getFilesUploaded());
        job.setWorkerId(event.getWorkerId());

        if ("FAILED".equals(event.getStatus())) {
            job.setErrorMessage(event.getErrorMessage());
        }

        profileJobRepository.save(job);

        // Check if 480p is complete (mark PLAYABLE)
        if ("480p".equals(profile) && "COMPLETED".equals(event.getStatus())) {
            log.info("✅ 480p complete - marking video PLAYABLE");
            videoRepository.updateStatus(videoId, "PLAYABLE");

            // Generate basic master playlist with just 480p
            try {
                playlistGenerator.generateBasicPlaylist(videoId);
            } catch (Exception e) {
                log.error("Failed to generate basic playlist: {}", e.getMessage());
            }
        }

        // Check if all profiles are complete (mark READY)
        long completedCount = profileJobRepository.countCompletedProfilesByVideoId(videoId);
        long totalCount = profileJobRepository.countTotalProfilesByVideoId(videoId);

        if (completedCount == totalCount) {
            log.info("✅ All {} profiles complete - marking video READY", totalCount);
            videoRepository.updateStatus(videoId, "READY");

            // Generate full master playlist with all profiles
            try {
                playlistGenerator.generateFullPlaylist(videoId);
            } catch (Exception e) {
                log.error("Failed to generate full playlist: {}", e.getMessage());
            }
        } else {
            log.info("Progress: {}/{} profiles complete for video {}",
                completedCount, totalCount, videoId);
        }
    }
}
