package com.cdn.orchestrator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_profile_jobs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"video_id", "profile"})
})
@Data
@NoArgsConstructor
public class VideoProfileJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private UUID videoId;

    @Column(nullable = false, length = 10)
    private String profile; // 480p, 720p, etc.

    @Column(nullable = false, length = 20)
    private String status = "QUEUED"; // QUEUED, PROCESSING, COMPLETED, FAILED

    @Column(name = "worker_id", length = 50)
    private String workerId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "files_count")
    private Integer filesCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VideoProfileJob(UUID videoId, String profile) {
        this.videoId = videoId;
        this.profile = profile;
        this.status = "QUEUED";
    }
}
