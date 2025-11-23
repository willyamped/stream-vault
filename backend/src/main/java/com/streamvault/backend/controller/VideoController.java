package com.streamvault.backend.controller;

import com.streamvault.backend.dto.StreamedFile;
import com.streamvault.backend.dto.VideoProcessedRequest;
import com.streamvault.backend.dto.VideoResponse;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.service.FileService;
import com.streamvault.backend.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private final VideoService videoService;
    private final FileService fileService;

    @GetMapping
    public ResponseEntity<Page<VideoResponse>> listVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<VideoResponse> videoPage = videoService
                .listVideos(pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(videoPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoResponse> getVideo(@PathVariable Long id) {
        VideoEntity video = videoService.getVideo(id);
        return ResponseEntity.ok(toResponse(video));
    }

    private VideoResponse toResponse(VideoEntity video) {
        return VideoResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .thumbnailPath(video.getThumbnailPath())
                .durationSeconds(video.getDurationSeconds())
                .readyForStreaming(video.getReadyForStreaming())
                .build();
    }

    @PostMapping("/callback/video-processed")
    public ResponseEntity<Void> callbackVideoProcessed(
            @RequestBody VideoProcessedRequest req) {

        log.info("Received video processed callback for videoId={}", req.getVideoId());
        videoService.updateVideoMetadata(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<byte[]> streamVideo(
            @PathVariable Long id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        try {
            VideoEntity video = videoService.getVideo(id);
            FileEntity file = video.getFile();

            StreamedFile streamed = fileService.streamFile(file, rangeHeader);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", streamed.contentType());
            headers.add("Accept-Ranges", "bytes");
            headers.add("Content-Length", String.valueOf(streamed.data().length));
            headers.add(
                "Content-Range",
                "bytes %d-%d/%d".formatted(
                    streamed.start(), streamed.end(), streamed.totalSize()
                )
            );

            return ResponseEntity
                    .status(streamed.partial() ? 206 : 200)
                    .headers(headers)
                    .body(streamed.data());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<VideoResponse> updateVideo(
            @PathVariable Long id,
            @RequestBody VideoResponse updateRequest) {

        VideoEntity updated = videoService.updateVideoTitleDescription(id, updateRequest.getTitle(), updateRequest.getDescription());
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<VideoResponse>> searchVideos(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageable = PageRequest.of(page, size);

        Page<VideoEntity> results = videoService.searchVideos(query, pageable);

        return ResponseEntity.ok(results.map(this::toResponse));
    }
}
