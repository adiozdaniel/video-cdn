package com.cdn.videoservice.controller;

import com.cdn.videoservice.dto.VideoListResponse;
import com.cdn.videoservice.model.Video;
import com.cdn.videoservice.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public ResponseEntity<VideoListResponse> listVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("GET /api/videos - page: {}, size: {}", page, size);
        VideoListResponse response = videoService.listVideos(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Video> getVideo(@PathVariable UUID id) {
        log.info("GET /api/videos/{}", id);
        Video video = videoService.getVideo(id);
        return ResponseEntity.ok(video);
    }
}
