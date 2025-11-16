package com.streamvault.backend.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoProcessedRequest {
    private Long videoId;
    private Long durationSeconds;
    private Integer width;
    private Integer height;
    private String thumbnailPath;
}
