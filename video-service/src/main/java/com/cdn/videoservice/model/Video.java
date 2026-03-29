package com.cdn.videoservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("videos")
public class Video {

    @Id
    private UUID id;

    private String filename;

    private Long size;

    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;
}
