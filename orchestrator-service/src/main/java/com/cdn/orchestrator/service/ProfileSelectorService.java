package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.VideoMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProfileSelectorService {

    public List<String> selectProfiles(VideoMetadata metadata) {
        List<String> profiles = new ArrayList<>();
        int height = metadata.height();

        // Add profiles based on source resolution (don't upscale)
        if (height >= 1080) profiles.add("1080p");
        if (height >= 720) profiles.add("720p");
        if (height >= 480) profiles.add("480p");
        if (height >= 360) profiles.add("360p");
        if (height >= 240) profiles.add("240p");

        if (profiles.isEmpty()) {
            throw new RuntimeException("Source video too low resolution (minimum 240p required)");
        }

        return profiles;
    }

    public String selectPreset(double duration) {
        if (duration > 600) return "ultrafast";  // > 10 min
        if (duration > 300) return "veryfast";   // > 5 min
        if (duration > 120) return "faster";     // > 2 min
        return "fast";                            // <= 2 min
    }
}
