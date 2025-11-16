package com.streamvault.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relationship to the underlying file
    @OneToOne
    @JoinColumn(name = "file_id")
    private FileEntity file;

    private String title;
    private String description;

    private String thumbnailPath;
    private Long durationSeconds;
    private Integer width;
    private Integer height;

    private LocalDateTime processedAt;
    private Boolean readyForStreaming;   
}
