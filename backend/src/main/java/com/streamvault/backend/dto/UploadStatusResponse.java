package com.streamvault.backend.dto;

import java.util.List;

import com.streamvault.backend.model.UploadStatus.Status;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadStatusResponse {
    private String uploadId;
    private Status status;
    private String fileName;
    private List<Integer> uploadedChunks;
}
