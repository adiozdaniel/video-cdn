package com.cdn.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioService {

    private final S3Client s3Client;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * Download video file for probing
     */
    public File downloadForProbing(UUID videoId, String filename) throws Exception {
        String key = String.format("raw/%s/%s", videoId, filename);

        log.info("Downloading video from MinIO for probing: {}", key);

        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);

        // Create temp file
        File tempFile = File.createTempFile("video-probe-" + videoId, ".mp4");
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            response.transferTo(fos);
        }

        log.info("Downloaded {} bytes to {}", tempFile.length(), tempFile.getAbsolutePath());

        return tempFile;
    }

    /**
     * Upload master playlist
     */
    public void uploadMasterPlaylist(UUID videoId, String content) throws Exception {
        String key = String.format("hls/%s/master.m3u8", videoId);

        log.info("Uploading master playlist: {}", key);

        File tempFile = File.createTempFile("master-" + videoId, ".m3u8");
        Files.writeString(tempFile.toPath(), content);

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/vnd.apple.mpegurl")
            .build();

        s3Client.putObject(request, tempFile.toPath());

        tempFile.delete();

        log.info("Master playlist uploaded successfully");
    }

    /**
     * Upload profile-specific playlist (Phase 4: for reassembled chunks)
     */
    public void uploadProfilePlaylist(UUID videoId, String profile, String content) throws Exception {
        String key = String.format("hls/%s/%s.m3u8", videoId, profile);

        log.info("Uploading {} playlist: {}", profile, key);

        File tempFile = File.createTempFile(profile + "-" + videoId, ".m3u8");
        Files.writeString(tempFile.toPath(), content);

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/vnd.apple.mpegurl")
            .build();

        s3Client.putObject(request, tempFile.toPath());

        tempFile.delete();

        log.info("{} playlist uploaded successfully", profile);
    }
}
