package com.cdn.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     *
     * Note: For Phase 4 initial implementation, we're creating a master playlist
     * that references chunk playlists. Future optimization: use FFmpeg concat
     * to merge into single playlist.
     *
     * @param videoId Video UUID
     * @param profile Profile (480p, 720p, etc.)
     * @param totalChunks Total number of chunks
     */
    public void reassembleChunks(UUID videoId, String profile, int totalChunks) throws Exception {
        log.info("Reassembling {} chunks for video {} profile {}", totalChunks, videoId, profile);

        if (totalChunks == 1) {
            log.info("Single chunk (whole video), no reassembly needed");
            return;
        }

        // Create temporary concat file for FFmpeg
        File concatFile = createConcatFile(videoId, profile, totalChunks);

        try {
            // For now, we'll create a master playlist that references chunk playlists
            // Future: Use FFmpeg concat to merge into single playlist
            String masterPlaylistContent = generateMasterPlaylistForChunks(videoId, profile, totalChunks);

            // Upload final playlist
            minioService.uploadProfilePlaylist(videoId, profile, masterPlaylistContent);

            log.info("Successfully reassembled {} chunks for {} {}", totalChunks, videoId, profile);

        } finally {
            // Clean up temp file
            if (concatFile != null && concatFile.exists()) {
                concatFile.delete();
            }
        }
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
     * This is a simplified approach for Phase 4 initial implementation
     */
    private String generateMasterPlaylistForChunks(UUID videoId, String profile, int totalChunks) {
        StringBuilder playlist = new StringBuilder();

        // For HLS, we create a playlist that lists all chunk playlists in sequence
        // The player will play them consecutively
        playlist.append("#EXTM3U\n");
        playlist.append("#EXT-X-VERSION:3\n");
        playlist.append("#EXT-X-TARGETDURATION:4\n");
        playlist.append("#EXT-X-MEDIA-SEQUENCE:0\n\n");

        for (int i = 0; i < totalChunks; i++) {
            // Reference each chunk's playlist
            playlist.append(String.format("# Chunk %d\n", i));
            playlist.append(String.format("%s_chunk%d.m3u8\n", profile, i));
        }

        playlist.append("#EXT-X-ENDLIST\n");

        return playlist.toString();
    }

    /**
     * Clean up chunk files from MinIO after successful reassembly
     * (For future implementation)
     */
    public void cleanupChunkFiles(UUID videoId, String profile, int totalChunks) {
        log.info("Cleaning up chunk files for {} {} (total: {})", videoId, profile, totalChunks);
        // TODO: Implement MinIO cleanup of chunk files
        // For now, we keep them as they're referenced in the master playlist
    }
}
