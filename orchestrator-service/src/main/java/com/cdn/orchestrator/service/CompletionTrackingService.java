package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.CompletionEvent;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoChunkJobRepository;
import com.cdn.orchestrator.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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

    public Mono<Void> handleProfileCompleted(CompletionEvent event) {
        UUID videoId = UUID.fromString(event.getVideoId());
        String profile = event.getProfile();

        if (event.getChunkId() != null) {
            return handleChunkCompleted(event, videoId, profile);
        } else {
            return handleWholeVideoCompleted(event, videoId, profile);
        }
    }

    private Mono<Void> handleWholeVideoCompleted(CompletionEvent event, UUID videoId, String profile) {
        log.info("📦 Handling whole video completion for {} - {}", videoId, profile);

        return profileJobRepository.findByVideoId(videoId)
            .filter(j -> j.getProfile().equals(profile))
            .next()
            .switchIfEmpty(Mono.error(new RuntimeException("Profile job not found: " + videoId + " - " + profile)))
            .flatMap(job -> {
                job.setStatus(event.getStatus());
                job.setCompletedAt(LocalDateTime.now());
                job.setDurationMs(event.getDurationMs() != null ? event.getDurationMs().intValue() : null);
                job.setFilesCount(event.getFilesUploaded());
                job.setWorkerId(event.getWorkerId());

                if ("FAILED".equals(event.getStatus())) {
                    job.setErrorMessage(event.getErrorMessage());
                }

                return profileJobRepository.save(job);
            })
            .flatMap(savedJob -> {
                if ("COMPLETED".equals(event.getStatus())) {
                    return updateOverallStatus(videoId, profile);
                }
                return Mono.empty();
            });
    }

    private Mono<Void> updateOverallStatus(UUID videoId, String profile) {
        return Mono.zip(
            profileJobRepository.countCompletedProfilesByVideoId(videoId),
            profileJobRepository.countTotalProfilesByVideoId(videoId)
        ).flatMap(tuple -> {
            long completedCount = tuple.getT1();
            long totalCount = tuple.getT2();

            Mono<Void> updateAction = Mono.empty();

            if (completedCount == 1) {
                log.info("✅ First profile complete ({}) - marking video PLAYABLE", profile);
                updateAction = videoRepository.updateStatus(videoId, "PLAYABLE").then();
            } else if (completedCount == totalCount) {
                log.info("✅ All {} profiles complete - marking video READY", totalCount);
                updateAction = videoRepository.updateStatus(videoId, "READY").then();
            }

            return updateAction.then(playlistGenerator.generateFullPlaylist(videoId));
        });
    }

    private Mono<Void> handleChunkCompleted(CompletionEvent event, UUID videoId, String profile) {
        int chunkId = event.getChunkId();
        log.info("📦 Handling chunk {} completion for {} - {}", chunkId, videoId, profile);

        return chunkJobRepository.findByVideoIdAndProfileAndChunkId(videoId, profile, chunkId)
            .switchIfEmpty(Mono.error(new RuntimeException("Chunk job not found: " + videoId + " - " + profile + " - chunk " + chunkId)))
            .flatMap(chunkJob -> {
                chunkJob.setStatus(event.getStatus());
                chunkJob.setCompletedAt(LocalDateTime.now());
                chunkJob.setDurationMs(event.getDurationMs() != null ? event.getDurationMs().intValue() : null);
                chunkJob.setWorkerId(event.getWorkerId());

                if ("FAILED".equals(event.getStatus())) {
                    chunkJob.setErrorMessage(event.getErrorMessage());
                }

                return chunkJobRepository.save(chunkJob);
            })
            .flatMap(savedChunkJob -> {
                if ("COMPLETED".equals(event.getStatus())) {
                    return Mono.zip(
                        chunkJobRepository.countCompletedChunks(videoId, profile),
                        chunkJobRepository.countTotalChunks(videoId, profile)
                    ).flatMap(tuple -> {
                        long completedChunks = tuple.getT1();
                        long totalChunks = tuple.getT2();

                        log.info("Chunk progress for {} {}: {}/{} chunks complete", videoId, profile, completedChunks, totalChunks);

                        if (completedChunks == totalChunks) {
                            log.info("🎉 All {} chunks complete for {} - {}, triggering reassembly", totalChunks, videoId, profile);
                            return reassemblyService.reassembleChunks(videoId, profile, (int) totalChunks)
                                .then(markProfileCompleteAfterReassembly(videoId, profile));
                        }
                        return Mono.empty();
                    });
                }
                return Mono.empty();
            });
    }

    private Mono<Void> markProfileCompleteAfterReassembly(UUID videoId, String profile) {
        return profileJobRepository.findByVideoId(videoId)
            .filter(j -> j.getProfile().equals(profile))
            .next()
            .switchIfEmpty(profileJobRepository.save(new VideoProfileJob(videoId, profile)))
            .flatMap(job -> {
                job.setStatus("COMPLETED");
                job.setCompletedAt(LocalDateTime.now());
                return profileJobRepository.save(job);
            })
            .flatMap(savedJob -> {
                log.info("Marked {} {} as COMPLETED after chunk reassembly", videoId, profile);
                return updateOverallStatus(videoId, profile);
            });
    }
}
