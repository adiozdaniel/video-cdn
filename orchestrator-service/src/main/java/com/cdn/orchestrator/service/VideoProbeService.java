package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.VideoMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoProbeService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoMetadata probeVideo(File videoFile) throws Exception {
        log.info("Probing video metadata for: {}", videoFile.getName());

        // Build ffprobe command
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration,size",
            "-show_entries", "stream=width,height,codec_name,codec_type",
            "-of", "json",
            videoFile.getAbsolutePath()
        );

        Process process = pb.start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("FFprobe timed out");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("FFprobe failed with exit code: " + process.exitValue());
        }

        // Parse JSON output
        JsonNode root = objectMapper.readTree(process.getInputStream());

        // Extract video stream
        JsonNode streams = root.get("streams");
        JsonNode videoStream = null;
        for (JsonNode stream : streams) {
            if ("video".equals(stream.get("codec_type").asText())) {
                videoStream = stream;
                break;
            }
        }

        if (videoStream == null) {
            throw new RuntimeException("No video stream found");
        }

        int width = videoStream.get("width").asInt();
        int height = videoStream.get("height").asInt();
        double duration = root.get("format").get("duration").asDouble();

        log.info("Video metadata: {}x{}, {:.1f}s", width, height, duration);

        return new VideoMetadata(width, height, duration);
    }
}
