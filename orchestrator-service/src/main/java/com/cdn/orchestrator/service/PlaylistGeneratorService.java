package com.cdn.orchestrator.service;

import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * Generate basic master playlist with first completed profile (for PLAYABLE state)
     */
    public void generateBasicPlaylist(UUID videoId) throws Exception {
        log.info("Generating basic master playlist for video: {}", videoId);

        List<VideoProfileJob> jobs = profileJobRepository.findByVideoId(videoId);

        // Find first completed profile
        VideoProfileJob firstCompleted = jobs.stream()
            .filter(j -> "COMPLETED".equals(j.getStatus()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No completed profiles found for PLAYABLE video"));

        String profile = firstCompleted.getProfile();
        int bandwidth = getBandwidth(profile);
        String resolution = getResolution(profile);

        String playlist = "#EXTM3U\n#EXT-X-VERSION:3\n\n";
        playlist += "#EXT-X-STREAM-INF:BANDWIDTH=" + bandwidth + ",RESOLUTION=" + resolution + "\n";
        playlist += profile + ".m3u8\n";

        minioService.uploadMasterPlaylist(videoId, playlist);
        log.info("Generated basic playlist with first completed profile: {}", profile);
    }

    /**
     * Generate full master playlist with all completed profiles (for READY state)
     */
    public void generateFullPlaylist(UUID videoId) throws Exception {
        log.info("Generating full master playlist for video: {}", videoId);

        List<VideoProfileJob> jobs = profileJobRepository.findByVideoId(videoId);

        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n\n");

        for (VideoProfileJob job : jobs) {
            if ("COMPLETED".equals(job.getStatus())) {
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
        }

        minioService.uploadMasterPlaylist(videoId, playlist.toString());
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
