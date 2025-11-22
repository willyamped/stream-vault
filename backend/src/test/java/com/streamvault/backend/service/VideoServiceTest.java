package com.streamvault.backend.service;

import com.streamvault.backend.dto.VideoProcessedRequest;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("createPendingVideo should save a video with file and readyForStreaming=false")
    void testCreatePendingVideo() {
        FileEntity file = new FileEntity();
        file.setFileName("test.mp4");

        VideoEntity savedVideo = new VideoEntity();
        savedVideo.setFile(file);
        savedVideo.setTitle("test.mp4");
        savedVideo.setReadyForStreaming(false);

        when(videoRepository.save(any(VideoEntity.class))).thenReturn(savedVideo);

        VideoEntity result = videoService.createPendingVideo(file);

        assertNotNull(result);
        assertEquals(file, result.getFile());
        assertEquals("test.mp4", result.getTitle());
        assertFalse(result.getReadyForStreaming());

        verify(videoRepository, times(1)).save(any(VideoEntity.class));
    }

    @Test
    @DisplayName("listVideos returns paginated videos")
    void testListVideos() {
        VideoEntity video = new VideoEntity();
        Page<VideoEntity> page = new PageImpl<>(Collections.singletonList(video));
        when(videoRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<VideoEntity> result = videoService.listVideos(Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        verify(videoRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getVideo returns video if exists")
    void testGetVideoExists() {
        VideoEntity video = new VideoEntity();
        video.setId(1L);

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));

        VideoEntity result = videoService.getVideo(1L);

        assertEquals(1L, result.getId());
        verify(videoRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getVideo throws exception if not found")
    void testGetVideoNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalStateException.class, () -> videoService.getVideo(1L));
        assertEquals("Video not found", exception.getMessage());

        verify(videoRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("updateVideoMetadata updates video and saves")
    void testUpdateVideoMetadata() {
        VideoEntity video = new VideoEntity();
        video.setId(1L);

        VideoProcessedRequest req = new VideoProcessedRequest();
        req.setVideoId(1L);
        req.setDurationSeconds(120L);
        req.setWidth(1920);
        req.setHeight(1080);
        req.setThumbnailPath("thumb.jpg");

        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(videoRepository.save(any(VideoEntity.class))).thenReturn(video);

        videoService.updateVideoMetadata(req);

        ArgumentCaptor<VideoEntity> captor = ArgumentCaptor.forClass(VideoEntity.class);
        verify(videoRepository).save(captor.capture());

        VideoEntity saved = captor.getValue();
        assertEquals(120L, saved.getDurationSeconds());
        assertEquals(1920, saved.getWidth());
        assertEquals(1080, saved.getHeight());
        assertEquals("thumb.jpg", saved.getThumbnailPath());
        assertTrue(saved.getReadyForStreaming());
    }
}
