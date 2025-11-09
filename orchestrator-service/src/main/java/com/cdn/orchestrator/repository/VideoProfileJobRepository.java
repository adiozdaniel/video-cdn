package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.VideoProfileJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VideoProfileJobRepository extends JpaRepository<VideoProfileJob, Long> {

    List<VideoProfileJob> findByVideoId(UUID videoId);

    @Query("SELECT COUNT(j) FROM VideoProfileJob j WHERE j.videoId = :videoId AND j.status = 'COMPLETED'")
    long countCompletedProfilesByVideoId(@Param("videoId") UUID videoId);

    @Query("SELECT COUNT(j) FROM VideoProfileJob j WHERE j.videoId = :videoId")
    long countTotalProfilesByVideoId(@Param("videoId") UUID videoId);

    @Query("SELECT j FROM VideoProfileJob j WHERE j.videoId = :videoId AND j.profile = '480p'")
    VideoProfileJob find480pJob(@Param("videoId") UUID videoId);
}
