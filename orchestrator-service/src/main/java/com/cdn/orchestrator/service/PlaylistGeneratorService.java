package com.cdn.orchestrator.service;

import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaylistGeneratorService {

    private final VideoProfileJobRepository profileJobRepository;
    private final MinioService minioService;

    private static final int DEFAULT_BANDWIDTH_480P = 1500000;
    private static final int DEFAULT_BANDWIDTH_720P = 3000000;
    private static final int DEFAULT_BANDWIDTH_1080P = 5000000;
    private static final int DEFAULT_BANDWIDTH_360P = 800000;
    private static final int DEFAULT_BANDWIDTH_240P = 400000;

    /**
     * Generate full master playlist with all completed profiles (for READY/PLAYABLE state)
     */
    public Mono<Void> generateFullPlaylist(UUID videoId) {
        log.info("Generating master playlist for video: {}", videoId);

        return profileJobRepository.findByVideoId(videoId)
            .filter(job -> "COMPLETED".equals(job.getStatus()))
            .collectList()
            .flatMap(completedJobs -> {
                if (completedJobs.isEmpty()) {
                    return Mono.empty();
                }

                StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n\n");

                for (var job : completedJobs) {
                    String profile = job.getProfile();
                    int bandwidth = getBandwidth(profile);
                    String resolution = getResolution(profile);

                    playlist.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(bandwidth)
                        .append(",RESOLUTION=")
                        .append(resolution)
                        .append("\n");
                    playlist.append(profile).append(".m3u8\n\n");
                }

                return minioService.uploadMasterPlaylist(videoId, playlist.toString());
            });
    }

    private int getBandwidth(String profile) {
        return switch (profile) {
            case "1080p" -> DEFAULT_BANDWIDTH_1080P;
            case "720p" -> DEFAULT_BANDWIDTH_720P;
            case "480p" -> DEFAULT_BANDWIDTH_480P;
            case "360p" -> DEFAULT_BANDWIDTH_360P;
            case "240p" -> DEFAULT_BANDWIDTH_240P;
            default -> DEFAULT_BANDWIDTH_480P;
        };
    }

    private String getResolution(String profile) {
        return switch (profile) {
            case "1080p" -> "1920x1080";
            case "720p" -> "1280x720";
            case "480p" -> "854x480";
            case "360p" -> "640x360";
            case "240p" -> "426x240";
            default -> "854x480";
        };
    }
}
