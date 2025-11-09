package com.cdn.orchestrator.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class ProfileJobMessage {
    private String videoId;
    private String profile;          // 480p, 720p, etc.
    private String inputPath;        // raw/uuid/filename.mp4
    private String outputPath;       // hls/uuid/
    private String preset;           // ultrafast, veryfast, fast
    private String priority;         // high, medium, low
    private Long timestamp;

    public ProfileJobMessage(UUID videoId, String profile, String filename, String preset) {
        this.videoId = videoId.toString();
        this.profile = profile;
        this.inputPath = String.format("raw/%s/%s", videoId, filename);
        this.outputPath = String.format("hls/%s/", videoId);
        this.preset = preset;
        this.priority = profile.equals("480p") ? "high" : "medium";
        this.timestamp = System.currentTimeMillis();
    }
}
