package com.cdn.orchestrator.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CompletionEvent {
    private String videoId;
    private String profile;
    private String workerId;
    private String status;         // COMPLETED or FAILED
    private Integer filesUploaded;
    private Long durationMs;
    private String errorMessage;
    private Long timestamp;

    // Phase 4: Chunk support (optional, null = whole video)
    private Integer chunkId;
}
