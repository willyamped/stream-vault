package com.streamvault.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uploadId;
    private String fileName;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status {
        INITIATED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
