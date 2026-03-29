package com.cdn.orchestrator.service;

import com.cdn.orchestrator.dto.VideoChunk;
import com.cdn.orchestrator.dto.VideoMetadata;
import com.cdn.orchestrator.model.Video;
import com.cdn.orchestrator.model.VideoProfileJob;
import com.cdn.orchestrator.repository.VideoChunkJobRepository;
import com.cdn.orchestrator.repository.VideoProfileJobRepository;
import com.cdn.orchestrator.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VideoOrchestrationServiceTest {

    @Mock
    private VideoRepository videoRepository;
    @Mock
    private VideoProfileJobRepository profileJobRepository;
    @Mock
    private VideoChunkJobRepository chunkJobRepository;
    @Mock
    private VideoProbeService probeService;
    @Mock
    private ProfileSelectorService profileSelector;
    @Mock
    private KafkaProfileJobProducer kafkaProducer;
    @Mock
    private MinioService minioService;
    @Mock
    private VideoChunkingService chunkingService;

    @InjectMocks
    private VideoOrchestrationService orchestrationService;

    private UUID videoId;
    private Video video;
    private File tempFile;

    @BeforeEach
    void setUp() {
        videoId = UUID.randomUUID();
        video = new Video();
        video.setId(videoId);
        video.setFilename("test.mp4");
        video.setStatus("UPLOADING");
        
        tempFile = mock(File.class);
    }

    @Test
    void testOrchestrateVideoWhole() {
        VideoMetadata metadata = new VideoMetadata(1920, 1080, 60.0);
        List<String> profiles = List.of("240p", "720p");

        when(tempFile.exists()).thenReturn(true);
        when(videoRepository.findById(videoId)).thenReturn(Mono.just(video));
        when(videoRepository.save(any(Video.class))).thenReturn(Mono.just(video));
        when(minioService.downloadForProbing(eq(videoId), anyString())).thenReturn(Mono.just(tempFile));
        when(probeService.probeVideo(tempFile)).thenReturn(Mono.just(metadata));
        when(profileSelector.selectProfiles(metadata)).thenReturn(profiles);
        when(profileSelector.selectPreset(60.0)).thenReturn("medium");
        when(chunkingService.needsChunking(60.0)).thenReturn(false);
        when(chunkingService.splitVideo(videoId, 60.0)).thenReturn(Collections.singletonList(new VideoChunk(videoId, 0, 0.0, 60.0)));
        
        when(profileJobRepository.save(any(VideoProfileJob.class))).thenReturn(Mono.just(new VideoProfileJob()));
        when(kafkaProducer.publishProfileJob(anyString(), any())).thenReturn(Mono.empty());

        StepVerifier.create(orchestrationService.orchestrateVideo(videoId))
            .verifyComplete();

        verify(videoRepository, times(1)).save(argThat(v -> v.getStatus().equals("PROCESSING")));
        verify(kafkaProducer, times(2)).publishProfileJob(anyString(), any());
        verify(tempFile).delete();
    }

    @Test
    void testOrchestrateVideoNotFound() {
        when(videoRepository.findById(videoId)).thenReturn(Mono.empty());
        when(videoRepository.updateStatus(any(UUID.class), eq("FAILED"))).thenReturn(Mono.just(1));

        StepVerifier.create(orchestrationService.orchestrateVideo(videoId))
            .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                                           throwable.getMessage().contains("Video not found"))
            .verify();
        
        verify(videoRepository).updateStatus(videoId, "FAILED");
    }
}
