package com.cdn.orchestrator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_chunk_jobs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"video_id", "profile", "chunk_id"})
})
@Data
@NoArgsConstructor
public class VideoChunkJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private UUID videoId;

    @Column(nullable = false, length = 10)
    private String profile;

    @Column(name = "chunk_id", nullable = false)
    private Integer chunkId;

    @Column(name = "start_time", nullable = false, precision = 10, scale = 2)
    private BigDecimal startTime;

    @Column(name = "end_time", nullable = false, precision = 10, scale = 2)
    private BigDecimal endTime;

    @Column(nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "worker_id", length = 50)
    private String workerId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public VideoChunkJob(UUID videoId, String profile, int chunkId, double startTime, double endTime) {
        this.videoId = videoId;
        this.profile = profile;
        this.chunkId = chunkId;
        this.startTime = BigDecimal.valueOf(startTime);
        this.endTime = BigDecimal.valueOf(endTime);
        this.status = "QUEUED";
        this.createdAt = LocalDateTime.now();
    }
}
