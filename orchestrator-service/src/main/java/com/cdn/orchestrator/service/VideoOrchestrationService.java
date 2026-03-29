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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    public Mono<Void> orchestrateVideo(UUID videoId) {
        log.info("🎬 Starting orchestration for video: {}", videoId);

        return videoRepository.findById(videoId)
            .switchIfEmpty(Mono.error(new RuntimeException("Video not found: " + videoId)))
            .flatMap(video -> {
                video.setStatus("PROCESSING");
                return videoRepository.save(video);
            })
            .flatMap(video -> minioService.downloadForProbing(videoId, video.getFilename())
                .flatMap(videoFile -> probeService.probeVideo(videoFile)
                    .flatMap(metadata -> {
                        List<String> profiles = profileSelector.selectProfiles(metadata);
                        String preset = profileSelector.selectPreset(metadata.duration());
                        boolean needsChunking = chunkingService.needsChunking(metadata.duration());
                        List<VideoChunk> chunks = chunkingService.splitVideo(videoId, metadata.duration());

                        Mono<Void> orchestrationProcess;
                        if (needsChunking) {
                            orchestrationProcess = orchestrateWithChunking(videoId, video.getFilename(), profiles, preset, chunks);
                        } else {
                            orchestrationProcess = orchestrateWholeVideo(videoId, video.getFilename(), profiles, preset);
                        }

                        return orchestrationProcess
                            .doFinally(signalType -> {
                                if (videoFile.exists()) {
                                    videoFile.delete();
                                }
                            });
                    })
                )
            )
            .doOnSuccess(v -> log.info("✅ Orchestration complete for video: {}", videoId))
            .onErrorResume(e -> {
                log.error("❌ Orchestration failed for video {}: {}", videoId, e.getMessage());
                return videoRepository.updateStatus(videoId, "FAILED")
                    .then(Mono.error(e));
            })
            .then();
    }

    private Mono<Void> orchestrateWholeVideo(UUID videoId, String filename,
                                            List<String> profiles, String preset) {
        return Flux.fromIterable(profiles)
            .flatMap(profile -> profileJobRepository.save(new VideoProfileJob(videoId, profile)))
            .thenMany(Flux.fromIterable(profiles))
            .flatMap(profile -> {
                String usedPreset = profile.equals("240p") ? "ultrafast" : preset;
                return publishJob(profile, videoId, filename, usedPreset);
            })
            .then();
    }

    private Mono<Void> orchestrateWithChunking(UUID videoId, String filename,
                                              List<String> profiles, String preset,
                                              List<VideoChunk> chunks) {
        return Flux.fromIterable(profiles)
            .flatMap(profile -> Flux.fromIterable(chunks)
                .flatMap(chunk -> chunkJobRepository.save(new VideoChunkJob(
                    videoId,
                    profile,
                    chunk.getChunkId(),
                    chunk.getStartTime(),
                    chunk.getEndTime()
                )))
            )
            .thenMany(Flux.fromIterable(profiles))
            .flatMap(profile -> Flux.fromIterable(chunks)
                .flatMap(chunk -> {
                    String usedPreset = profile.equals("240p") ? "ultrafast" : preset;
                    return publishChunkJob(profile, videoId, filename, usedPreset, chunk);
                })
            )
            .then()
            .doOnSuccess(v -> log.info("Published chunk jobs for video {}", videoId));
    }

    private Mono<Void> publishJob(String profile, UUID videoId, String filename, String preset) {
        ProfileJobMessage job = new ProfileJobMessage(videoId, profile, filename, preset);
        return kafkaProducer.publishProfileJob(profile, job);
    }

    private Mono<Void> publishChunkJob(String profile, UUID videoId,
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
        return kafkaProducer.publishProfileJob(profile, job);
    }
}
