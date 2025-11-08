package com.cdn.videoservice.service;

import com.cdn.videoservice.dto.VideoListResponse;
import com.cdn.videoservice.model.Video;
import com.cdn.videoservice.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public VideoListResponse listVideos(int page, int size) {
        // Limit page size to prevent abuse
        size = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, size);
        Page<Video> videoPage = videoRepository.findAllActive(pageable);

        log.info("Listed {} videos (page {}, size {})", videoPage.getContent().size(), page, size);

        return new VideoListResponse(
            videoPage.getContent(),
            videoPage.getTotalElements(),
            page,
            size
        );
    }

    public Video getVideo(UUID id) {
        return videoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Video not found: " + id));
    }
}
