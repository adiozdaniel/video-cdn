package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.Video;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VideoRepository extends R2dbcRepository<Video, UUID> {

    @Modifying
    @Query("UPDATE videos SET status = :status WHERE id = :videoId")
    Mono<Integer> updateStatus(@Param("videoId") UUID videoId, @Param("status") String status);
}
