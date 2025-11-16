package com.streamvault.backend.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoResponse {
    private Long id;
    private String title;
    private String description;
    private String thumbnailPath;
    private boolean readyForStreaming;
    private Long durationSeconds;
}
