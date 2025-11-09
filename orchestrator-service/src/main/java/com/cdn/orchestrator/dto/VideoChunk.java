package com.cdn.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoChunk {
    private UUID videoId;
    private int chunkId;
    private double startTime;
    private double endTime;

    public double getDuration() {
        return endTime - startTime;
    }
}
