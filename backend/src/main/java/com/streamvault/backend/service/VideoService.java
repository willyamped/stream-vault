package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.dto.VideoProcessedRequest;
import com.streamvault.backend.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public VideoEntity createPendingVideo(FileEntity file) {
        return videoRepository.save(
                VideoEntity.builder()
                        .file(file)
                        .title(file.getFileName())
                        .readyForStreaming(false)
                        .build()
        );
    }

    public Page<VideoEntity> listVideos(Pageable pageable) {
      return videoRepository.findAll(pageable);
    }

    public VideoEntity getVideo(Long id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Video not found"));
    }

    public VideoEntity updateVideoMetadata(VideoProcessedRequest req) {
        VideoEntity video = getVideo(req.getVideoId());

        video.setDurationSeconds(req.getDurationSeconds());
        video.setWidth(req.getWidth());
        video.setHeight(req.getHeight());
        video.setThumbnailPath(req.getThumbnailPath());
        video.setReadyForStreaming(true);

        return videoRepository.save(video);
    }
}
