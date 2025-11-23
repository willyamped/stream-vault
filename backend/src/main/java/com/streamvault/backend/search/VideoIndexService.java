package com.streamvault.backend.search;

import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.repository.VideoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoIndexService {

    private final VideoSearchRepository videoSearchRepository;
    private final VideoRepository videoRepository;

    public void indexVideo(VideoEntity video) {

        VideoDocument doc = VideoDocument.builder()
                .id(video.getId().toString())
                .title(video.getTitle())
                .description(video.getDescription())
                .thumbnailPath(video.getThumbnailPath())
                .durationSeconds(video.getDurationSeconds())
                .width(video.getWidth())
                .height(video.getHeight())
                .processedAt(video.getProcessedAt() != null
                    ? video.getProcessedAt().atZone(ZoneOffset.UTC).toInstant()
                    : Instant.now())
                .readyForStreaming(video.getReadyForStreaming())
                .build();

        videoSearchRepository.save(doc);
    }

    public Page<VideoEntity> searchVideos(String query, Pageable pageable) {
        try {
            Page<VideoDocument> esResults = videoSearchRepository
                    .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query, pageable);

            List<VideoEntity> videos = esResults.getContent().stream()
                    .map(doc -> videoRepository.findById(Long.parseLong(doc.getId()))
                            .orElse(null))
                    .filter(v -> v != null)
                    .toList();

            return new PageImpl<>(videos, pageable, esResults.getTotalElements());

        } catch (RuntimeException ex) {
            return videoRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query, pageable);
        }
    }

}
