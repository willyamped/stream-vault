package com.streamvault.backend.service;

import com.streamvault.backend.kafka.FileUploadProducer;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.repository.UploadStatusRepository;
import com.streamvault.backend.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FileService fileService;
    private final UploadStatusRepository uploadStatusRepository;
    private final FileUploadProducer fileUploadProducer;

    private static final String TEMP_DIR = "uploads/tmp";
    private static final String CHUNK_KEY_PREFIX = "upload:";

    public String initialiseUpload() {
        String uploadId = UUID.randomUUID().toString();
        uploadStatusRepository.save(
                UploadStatus.builder()
                        .uploadId(uploadId)
                        .status(UploadStatus.Status.INITIATED)
                        .build()
        );
        return uploadId;
    }

    public void saveChunk(String uploadId, int chunkNumber, MultipartFile file) throws IOException {
        Path uploadDir = Paths.get(TEMP_DIR, uploadId);
        Files.createDirectories(uploadDir);

        Path chunkPath = uploadDir.resolve("chunk_" + chunkNumber);
        file.transferTo(chunkPath);

        // store in Redis
        String key = CHUNK_KEY_PREFIX + uploadId;
        redisTemplate.opsForHash().put(key, String.valueOf(chunkNumber), chunkPath.toString());
    }

    public void completeUpload(String uploadId, String fileName) {
        UploadStatus status = uploadStatusRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("UploadId not found"));
        status.setStatus(UploadStatus.Status.PROCESSING);
        status.setFileName(fileName);
        uploadStatusRepository.save(status);

        // Publish Kafka event
        fileUploadProducer.sendUploadCompleted(uploadId, fileName);
    }

    public FileEntity mergeChunksAndSaveFile(String uploadId, String fileName) throws IOException {
        UploadStatus status = uploadStatusRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("Invalid uploadId"));

        String key = CHUNK_KEY_PREFIX + uploadId;

        try {
            List<String> chunkPaths = redisTemplate.<String, String>opsForHash().entries(key)
                    .entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                    .map(e -> e.getValue())
                    .collect(Collectors.toList());

            if (chunkPaths.isEmpty()) {
                throw new IllegalStateException("No chunks found for uploadId: " + uploadId);
            }

            Path finalFile = Paths.get(TEMP_DIR, fileName);
            try (OutputStream os = Files.newOutputStream(finalFile)) {
                for (String chunkPath : chunkPaths) {
                    Files.copy(Paths.get(chunkPath), os);
                }
            }

            redisTemplate.delete(key);

            byte[] fileBytes = Files.readAllBytes(finalFile);
            String hash = Util.computeHash(fileBytes);

            if (fileService.fileExists(hash)) {
                throw new IllegalStateException("File already exists with hash: " + hash);
            }

            String contentType = Files.probeContentType(finalFile);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            FileEntity savedFile = fileService.saveFile(
                    fileName,
                    contentType,
                    finalFile.toFile().length(),
                    hash,
                    finalFile.toFile()
            );

            status.setStatus(UploadStatus.Status.COMPLETED);
            uploadStatusRepository.save(status);

            return savedFile;

        } catch (Exception e) {
            status.setStatus(UploadStatus.Status.FAILED);
            uploadStatusRepository.save(status);
            throw new IllegalStateException(
                "❌ Failed to merge chunks for uploadId=" + uploadId +
                ", fileName=" + fileName +
                ": " + e.getMessage(), 
                e
            );
        }
    }


    public UploadStatus getUploadStatus(String uploadId) {
        return uploadStatusRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("Invalid uploadId"));
    }
}
