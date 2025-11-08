package com.cdn.videoservice.repository;

import com.cdn.videoservice.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    @Query("SELECT v FROM Video v WHERE v.deletedAt IS NULL ORDER BY v.createdAt DESC")
    Page<Video> findAllActive(Pageable pageable);
}
