package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.VideoChunkJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoChunkJobRepository extends JpaRepository<VideoChunkJob, Long> {

    List<VideoChunkJob> findByVideoIdAndProfile(UUID videoId, String profile);

    List<VideoChunkJob> findByVideoId(UUID videoId);

    Optional<VideoChunkJob> findByVideoIdAndProfileAndChunkId(UUID videoId, String profile, int chunkId);

    @Query("SELECT COUNT(j) FROM VideoChunkJob j WHERE j.videoId = :videoId AND j.profile = :profile AND j.status = 'COMPLETED'")
    long countCompletedChunks(@Param("videoId") UUID videoId, @Param("profile") String profile);

    @Query("SELECT COUNT(j) FROM VideoChunkJob j WHERE j.videoId = :videoId AND j.profile = :profile")
    long countTotalChunks(@Param("videoId") UUID videoId, @Param("profile") String profile);
}
