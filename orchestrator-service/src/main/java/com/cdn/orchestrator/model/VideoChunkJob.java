package com.cdn.orchestrator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("video_chunk_jobs")
@Data
@NoArgsConstructor
public class VideoChunkJob {

    @Id
    private Long id;

    @Column("video_id")
    private UUID videoId;

    private String profile;

    @Column("chunk_id")
    private Integer chunkId;

    @Column("start_time")
    private BigDecimal startTime;

    @Column("end_time")
    private BigDecimal endTime;

    private String status = "QUEUED";

    @Column("worker_id")
    private String workerId;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("duration_ms")
    private Integer durationMs;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

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
