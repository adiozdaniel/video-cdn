package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.VideoProfileJob;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VideoProfileJobRepository extends R2dbcRepository<VideoProfileJob, Long> {

    Flux<VideoProfileJob> findByVideoId(UUID videoId);

    @Query("SELECT COUNT(*) FROM video_profile_jobs WHERE video_id = :videoId AND status = 'COMPLETED'")
    Mono<Long> countCompletedProfilesByVideoId(@Param("videoId") UUID videoId);

    @Query("SELECT COUNT(*) FROM video_profile_jobs WHERE video_id = :videoId")
    Mono<Long> countTotalProfilesByVideoId(@Param("videoId") UUID videoId);

    @Query("SELECT * FROM video_profile_jobs WHERE video_id = :videoId AND profile = '480p' LIMIT 1")
    Mono<VideoProfileJob> find480pJob(@Param("videoId") UUID videoId);
}
