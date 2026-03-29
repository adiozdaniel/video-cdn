package com.cdn.orchestrator.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("videos")
@Data
public class Video {

    @Id
    private UUID id;

    private String filename;

    private Long size;

    private Integer duration; // seconds

    private String status = "UPLOADING";

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;

    @Column("processed_at")
    private LocalDateTime processedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;

    private String metadata; // JSON metadata
}
