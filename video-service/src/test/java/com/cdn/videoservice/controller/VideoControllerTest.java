package com.cdn.videoservice.controller;

import com.cdn.videoservice.dto.VideoListResponse;
import com.cdn.videoservice.model.Video;
import com.cdn.videoservice.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@WebFluxTest(VideoController.class)
public class VideoControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private VideoService videoService;

    @Test
    public void testListVideos() {
        Video video = new Video();
        video.setId(UUID.randomUUID());
        video.setFilename("test.mp4");
        video.setSize(1000L);
        video.setStatus("READY");
        video.setCreatedAt(LocalDateTime.now());

        VideoListResponse response = new VideoListResponse(
            Collections.singletonList(video),
            1,
            0,
            50
        );

        when(videoService.listVideos(anyInt(), anyInt())).thenReturn(Mono.just(response));

        webTestClient.get().uri("/api/videos")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.videos[0].filename").isEqualTo("test.mp4")
            .jsonPath("$.total").isEqualTo(1);
    }

    @Test
    public void testGetVideo() {
        UUID id = UUID.randomUUID();
        Video video = new Video();
        video.setId(id);
        video.setFilename("test.mp4");
        video.setStatus("READY");

        when(videoService.getVideo(id)).thenReturn(Mono.just(video));

        webTestClient.get().uri("/api/videos/" + id)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.filename").isEqualTo("test.mp4");
    }

    @Test
    public void testGetVideoNotFound() {
        UUID id = UUID.randomUUID();
        when(videoService.getVideo(id)).thenReturn(Mono.error(new RuntimeException("Video not found")));

        webTestClient.get().uri("/api/videos/" + id)
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
