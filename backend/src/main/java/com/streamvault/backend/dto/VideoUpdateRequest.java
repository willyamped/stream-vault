package com.streamvault.backend.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUpdateRequest {
    private String title;
    private String description;
}
