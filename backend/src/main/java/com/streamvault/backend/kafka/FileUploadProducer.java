package com.streamvault.backend.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileUploadProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendUploadCompleted(String uploadId, String fileName) {
        kafkaTemplate.send("file-upload-completed", uploadId + "|" + fileName);
    }
}
