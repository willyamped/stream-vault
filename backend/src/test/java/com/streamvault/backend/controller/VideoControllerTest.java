package com.streamvault.backend.controller;

import com.streamvault.backend.dto.StreamedFile;
import com.streamvault.backend.dto.VideoProcessedRequest;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.service.FileService;
import com.streamvault.backend.service.VideoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoService videoService;

    @MockitoBean
    private FileService fileService;
    
    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private ZSetOperations<String, Object> zSetOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.removeRangeByScore(any(), anyDouble(), anyDouble())).thenReturn(0L); // mock behavior
    }

    @Test
    @DisplayName("listVideos returns paginated JSON response")
    void testListVideos() throws Exception {
        VideoEntity entity = new VideoEntity();
        entity.setId(1L);
        entity.setTitle("Test Video");
        entity.setDescription("desc");
        entity.setThumbnailPath("thumb.jpg");
        entity.setDurationSeconds(120L);
        entity.setReadyForStreaming(true);

        Page<VideoEntity> page = new PageImpl<>(Collections.singletonList(entity));
        when(videoService.listVideos(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/videos?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].title").value("Test Video"));
    }

    @Test
    @DisplayName("getVideo returns single JSON DTO")
    void testGetVideo() throws Exception {
        VideoEntity entity = new VideoEntity();
        entity.setId(2L);
        entity.setTitle("Another Video");
        entity.setDescription("desc2");
        entity.setThumbnailPath("thumb2.jpg");
        entity.setDurationSeconds(60L);
        entity.setReadyForStreaming(false);

        when(videoService.getVideo(2L)).thenReturn(entity);

        mockMvc.perform(get("/api/videos/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.title").value("Another Video"));
    }

    @Test
    @DisplayName("callbackVideoProcessed triggers service and returns OK")
    void testCallbackVideoProcessed() throws Exception {
        VideoProcessedRequest request = new VideoProcessedRequest();
        request.setVideoId(3L);

        doNothing().when(videoService).updateVideoMetadata(any(VideoProcessedRequest.class));

        mockMvc.perform(post("/api/videos/callback/video-processed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"videoId\": 3 }") )
                .andExpect(status().isOk());

        verify(videoService, times(1)).updateVideoMetadata(any(VideoProcessedRequest.class));
    }

    @Test
    @DisplayName("streamVideo returns binary content with correct headers for range request")
    void testStreamVideo() throws Exception {
        Long id = 4L;
        String range = "bytes=0-";

        VideoEntity videoEntity = new VideoEntity();
        FileEntity file = new FileEntity();
        file.setFileType("video/mp4");
        videoEntity.setFile(file);

        byte[] expectedBytes = new byte[]{1, 2, 3};

        StreamedFile streamed = new StreamedFile(
                expectedBytes,
                0,
                2,
                3,
                "video/mp4",
                true
        );

        when(videoService.getVideo(id)).thenReturn(videoEntity);
        when(fileService.streamFile(file, range)).thenReturn(streamed);

        mockMvc.perform(get("/api/videos/stream/4").header("Range", range))
                .andExpect(status().isPartialContent()) // 206
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("Content-Range", "bytes 0-2/3"))
                .andExpect(content().bytes(expectedBytes));
    }
    
    @Test
    @DisplayName("streamVideo returns 500 on exception from fileService")
    void testStreamVideoException() throws Exception {
        Long id = 5L;
        String range = null;

        VideoEntity videoEntity = new VideoEntity();
        FileEntity file = new FileEntity();
        videoEntity.setFile(file);

        when(videoService.getVideo(id)).thenReturn(videoEntity);
        when(fileService.streamFile(file, range)).thenThrow(new RuntimeException("IO error"));

        mockMvc.perform(get("/api/videos/stream/5"))
                .andExpect(status().isInternalServerError());
    }
}