package com.cdn.videoservice.repository;

import com.cdn.videoservice.model.Video;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VideoRepository extends R2dbcRepository<Video, UUID> {

    Flux<Video> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Mono<Long> countByDeletedAtIsNull();
}
