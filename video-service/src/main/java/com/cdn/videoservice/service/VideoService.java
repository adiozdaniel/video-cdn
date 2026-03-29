package com.cdn.videoservice.service;

import com.cdn.videoservice.dto.VideoListResponse;
import com.cdn.videoservice.model.Video;
import com.cdn.videoservice.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public Mono<VideoListResponse> listVideos(int page, int size) {
        // Limit page size to prevent abuse
        int finalSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, finalSize);

        return Mono.zip(
            videoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(pageable).collectList(),
            videoRepository.countByDeletedAtIsNull()
        ).map(tuple -> {
            log.info("Listed {} videos (page {}, size {})", tuple.getT1().size(), page, finalSize);
            return new VideoListResponse(
                tuple.getT1(),
                tuple.getT2(),
                page,
                finalSize
            );
        });
    }

    public Mono<Video> getVideo(UUID id) {
        return videoRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Video not found: " + id)));
    }
}
