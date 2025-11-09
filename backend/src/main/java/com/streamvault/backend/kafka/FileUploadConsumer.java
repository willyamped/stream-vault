package com.streamvault.backend.kafka;

import com.streamvault.backend.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileUploadConsumer {

    private final ChunkUploadService chunkUploadService;

    @KafkaListener(topics = "file-upload-completed")
    public void consumeUpload(String message) {
        String[] parts = message.split("\\|");
        String uploadId = parts[0];
        String fileName = parts[1];

        try {
            chunkUploadService.mergeChunksAndSaveFile(uploadId, fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
