package com.streamvault.backend.dto;

import com.streamvault.backend.model.UploadStatus.Status;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadStatusResponse {
    private String uploadId;
    private Status status;
    private String fileName;
}
