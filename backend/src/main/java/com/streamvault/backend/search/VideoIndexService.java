package com.streamvault.backend.search;

import com.streamvault.backend.model.VideoEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class VideoIndexService {

    private final VideoSearchRepository videoSearchRepository;

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
}
