package com.cdn.orchestrator.repository;

import com.cdn.orchestrator.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    @Modifying
    @Query("UPDATE Video v SET v.status = :status WHERE v.id = :videoId")
    void updateStatus(@Param("videoId") UUID videoId, @Param("status") String status);
}
