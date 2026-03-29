package com.cdn.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkReassemblyService {

    private final MinioService minioService;

    /**
     * Reassemble chunks into final HLS playlist for a given profile.
     * This creates a master playlist that references all chunk playlists.
     */
    public Mono<Void> reassembleChunks(UUID videoId, String profile, int totalChunks) {
        log.info("Reassembling {} chunks for video {} profile {}", totalChunks, videoId, profile);

        if (totalChunks == 1) {
            log.info("Single chunk (whole video), no reassembly needed");
            return Mono.empty();
        }

        return Mono.fromCallable(() -> createConcatFile(videoId, profile, totalChunks))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(concatFile -> {
                String masterPlaylistContent = generateMasterPlaylistForChunks(videoId, profile, totalChunks);
                return minioService.uploadProfilePlaylist(videoId, profile, masterPlaylistContent)
                    .doFinally(signalType -> {
                        if (concatFile.exists()) {
                            concatFile.delete();
                        }
                    });
            })
            .doOnSuccess(v -> log.info("Successfully reassembled {} chunks for {} {}", totalChunks, videoId, profile))
            .then();
    }

    /**
     * Create FFmpeg concat demuxer file
     */
    private File createConcatFile(UUID videoId, String profile, int totalChunks) throws IOException {
        File concatFile = File.createTempFile("concat-" + videoId + "-" + profile, ".txt");

        try (FileWriter writer = new FileWriter(concatFile)) {
            for (int i = 0; i < totalChunks; i++) {
                writer.write(String.format("file 'hls/%s/%s_chunk%d.m3u8'\n", videoId, profile, i));
            }
        }

        return concatFile;
    }

    /**
     * Generate master playlist that references all chunk playlists
     */
    private String generateMasterPlaylistForChunks(UUID videoId, String profile, int totalChunks) {
        StringBuilder playlist = new StringBuilder();

        playlist.append("#EXTM3U\n");
        playlist.append("#EXT-X-VERSION:3\n");
        playlist.append("#EXT-X-TARGETDURATION:4\n");
        playlist.append("#EXT-X-MEDIA-SEQUENCE:0\n\n");

        for (int i = 0; i < totalChunks; i++) {
            playlist.append(String.format("# Chunk %d\n", i));
            playlist.append(String.format("%s_chunk%d.m3u8\n", profile, i));
        }

        playlist.append("#EXT-X-ENDLIST\n");

        return playlist.toString();
    }
}
