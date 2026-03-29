package com.cdn.orchestrator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("video_profile_jobs")
@Data
@NoArgsConstructor
public class VideoProfileJob {

    @Id
    private Long id;

    @Column("video_id")
    private UUID videoId;

    private String profile; // 480p, 720p, etc.

    private String status = "QUEUED"; // QUEUED, PROCESSING, COMPLETED, FAILED

    @Column("worker_id")
    private String workerId;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("duration_ms")
    private Integer durationMs;

    @Column("files_count")
    private Integer filesCount;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    public VideoProfileJob(UUID videoId, String profile) {
        this.videoId = videoId;
        this.profile = profile;
        this.status = "QUEUED";
        this.createdAt = LocalDateTime.now();
    }
}
