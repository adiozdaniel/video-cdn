package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.model.VideoChunkJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoChunkJobRepository;
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
    private final VideoChunkJobRepository chunkJobRepository;
    private final VideoRepository videoRepository;
    private final PlaylistGeneratorService playlistGenerator;
    private final ChunkReassemblyService reassemblyService;

    @Transactional
    public void handleProfileCompleted(CompletionEvent event) {
        UUID videoId = UUID.fromString(event.getVideoId());
        String profile = event.getProfile();

        // Phase 4: Check if this is a chunk completion or whole video completion
        if (event.getChunkId() != null) {
            handleChunkCompleted(event, videoId, profile);
        } else {
            handleWholeVideoCompleted(event, videoId, profile);
        }
    }

    /**
     * Phase 3: Handle whole video completion
     */
    private void handleWholeVideoCompleted(CompletionEvent event, UUID videoId, String profile) {
        log.info("📦 Phase 3: Handling whole video completion for {} - {}", videoId, profile);

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

        // Progressive enhancement: Update playlist after EACH profile completion
        if ("COMPLETED".equals(event.getStatus())) {
            long completedCount = profileJobRepository.countCompletedProfilesByVideoId(videoId);
            long totalCount = profileJobRepository.countTotalProfilesByVideoId(videoId);

            if (completedCount == 1) {
                // First profile done - make video playable immediately!
                log.info("✅ First profile complete ({}) - marking video PLAYABLE", profile);
                videoRepository.updateStatus(videoId, "PLAYABLE");
            } else if (completedCount < totalCount) {
                // Additional profile complete - keep PLAYABLE, add to playlist
                log.info("✅ Additional profile complete ({}): {}/{} profiles done",
                    profile, completedCount, totalCount);
            }

            // Regenerate playlist with all completed profiles so far (progressive enhancement!)
            try {
                playlistGenerator.generateFullPlaylist(videoId);
                log.info("Updated master playlist with {} completed profile(s)", completedCount);
            } catch (Exception e) {
                log.error("Failed to generate playlist: {}", e.getMessage());
            }

            // Check if all profiles are complete (mark READY)
            if (completedCount == totalCount) {
                log.info("✅ All {} profiles complete - marking video READY", totalCount);
                videoRepository.updateStatus(videoId, "READY");
            }
        }
    }

    /**
     * Phase 4: Handle chunk completion
     */
    private void handleChunkCompleted(CompletionEvent event, UUID videoId, String profile) {
        int chunkId = event.getChunkId();
        log.info("📦 Phase 4: Handling chunk {} completion for {} - {}", chunkId, videoId, profile);

        // Update chunk job status
        VideoChunkJob chunkJob = chunkJobRepository
            .findByVideoIdAndProfileAndChunkId(videoId, profile, chunkId)
            .orElse(null);

        if (chunkJob == null) {
            log.warn("Chunk job not found: {} - {} - chunk {}", videoId, profile, chunkId);
            return;
        }

        chunkJob.setStatus(event.getStatus());
        chunkJob.setCompletedAt(LocalDateTime.now());
        chunkJob.setDurationMs(event.getDurationMs() != null ? event.getDurationMs().intValue() : null);
        chunkJob.setWorkerId(event.getWorkerId());

        if ("FAILED".equals(event.getStatus())) {
            chunkJob.setErrorMessage(event.getErrorMessage());
        }

        chunkJobRepository.save(chunkJob);

        // Check if all chunks for this profile are complete
        long completedChunks = chunkJobRepository.countCompletedChunks(videoId, profile);
        long totalChunks = chunkJobRepository.countTotalChunks(videoId, profile);

        log.info("Chunk progress for {} {}: {}/{} chunks complete",
            videoId, profile, completedChunks, totalChunks);

        if (completedChunks == totalChunks && "COMPLETED".equals(event.getStatus())) {
            log.info("🎉 All {} chunks complete for {} - {}, triggering reassembly",
                totalChunks, videoId, profile);

            // Trigger reassembly
            try {
                reassemblyService.reassembleChunks(videoId, profile, (int) totalChunks);

                // After reassembly, mark profile as complete in Phase 3 job table
                // (for compatibility with existing PLAYABLE/READY logic)
                markProfileCompleteAfterReassembly(videoId, profile);

            } catch (Exception e) {
                log.error("Failed to reassemble chunks for {} {}: {}", videoId, profile, e.getMessage(), e);
            }
        }
    }

    /**
     * After chunk reassembly, mark profile job as complete
     * This triggers the existing PLAYABLE/READY logic
     */
    private void markProfileCompleteAfterReassembly(UUID videoId, String profile) {
        // Find or create profile job
        List<VideoProfileJob> jobs = profileJobRepository.findByVideoId(videoId);
        VideoProfileJob job = jobs.stream()
            .filter(j -> j.getProfile().equals(profile))
            .findFirst()
            .orElseGet(() -> {
                VideoProfileJob newJob = new VideoProfileJob(videoId, profile);
                return profileJobRepository.save(newJob);
            });

        job.setStatus("COMPLETED");
        job.setCompletedAt(LocalDateTime.now());
        profileJobRepository.save(job);

        log.info("Marked {} {} as COMPLETED after chunk reassembly", videoId, profile);

        // Progressive enhancement: Update playlist after EACH profile reassembly
        long completedCount = profileJobRepository.countCompletedProfilesByVideoId(videoId);
        long totalCount = profileJobRepository.countTotalProfilesByVideoId(videoId);

        if (completedCount == 1) {
            // First profile done - make video playable immediately!
            log.info("✅ First profile complete after reassembly ({}) - marking video PLAYABLE", profile);
            videoRepository.updateStatus(videoId, "PLAYABLE");
        } else if (completedCount < totalCount) {
            // Additional profile reassembled - keep PLAYABLE, add to playlist
            log.info("✅ Additional profile reassembled ({}): {}/{} profiles complete",
                profile, completedCount, totalCount);
        }

        // Regenerate playlist with all completed profiles so far (progressive enhancement!)
        try {
            playlistGenerator.generateFullPlaylist(videoId);
            log.info("Updated master playlist with {} completed profile(s)", completedCount);
        } catch (Exception e) {
            log.error("Failed to generate playlist: {}", e.getMessage());
        }

        // Check if all profiles are done (mark READY)
        if (completedCount == totalCount) {
            log.info("✅ All {} profiles complete - marking video READY", totalCount);
            videoRepository.updateStatus(videoId, "READY");
        }
    }
}
