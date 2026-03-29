package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.VideoChunkJob;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VideoChunkJobRepository extends R2dbcRepository<VideoChunkJob, Long> {

    Flux<VideoChunkJob> findByVideoIdAndProfile(UUID videoId, String profile);

    Flux<VideoChunkJob> findByVideoId(UUID videoId);

    Mono<VideoChunkJob> findByVideoIdAndProfileAndChunkId(UUID videoId, String profile, int chunkId);

    @Query("SELECT COUNT(*) FROM video_chunk_jobs WHERE video_id = :videoId AND profile = :profile AND status = 'COMPLETED'")
    Mono<Long> countCompletedChunks(@Param("videoId") UUID videoId, @Param("profile") String profile);

    @Query("SELECT COUNT(*) FROM video_chunk_jobs WHERE video_id = :videoId AND profile = :profile")
    Mono<Long> countTotalChunks(@Param("videoId") UUID videoId, @Param("profile") String profile);
}
