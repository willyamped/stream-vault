package com.streamvault.backend.service;

import com.streamvault.backend.kafka.FileUploadProducer;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.repository.UploadStatusRepository;
import com.streamvault.backend.util.Util;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FileService fileService;
    private final UploadStatusRepository uploadStatusRepository;
    private final FileUploadProducer fileUploadProducer;
    private final MinioClient minioClient;

    private static final String CHUNK_BUCKET = "upload-chunks";
    private static final String CHUNK_KEY_PREFIX = "upload:";
    private static final Duration UPLOAD_TTL = Duration.ofHours(24);

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

    public boolean saveChunk(String uploadId, int chunkNumber, MultipartFile file) {
        String key = CHUNK_KEY_PREFIX + uploadId;
        String redisField = String.valueOf(chunkNumber);
        String objectName = uploadId + "/chunk_" + chunkNumber;
        
        if (redisTemplate.opsForHash().hasKey(key, redisField)) {
            return false;
        }

        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(CHUNK_BUCKET)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType("application/octet-stream")
                    .build()
            );

            redisTemplate.opsForHash()
                    .put(key, redisField, objectName);

            if (redisTemplate.getExpire(key) == -1) {
                redisTemplate.expire(key, UPLOAD_TTL);
            }
            return true;

        } catch (Exception e) {
            throw new RuntimeException(
                String.format("Failed to store chunk %d for uploadId=%s to MinIO", chunkNumber, uploadId),
                e
            );
        }
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
            List<String> chunkObjects = redisTemplate.<String, String>opsForHash()
                .entries(key)
                .entrySet().stream()
                .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

            Path finalFile = Files.createTempFile(uploadId, fileName);

            try (OutputStream os = Files.newOutputStream(finalFile)) {
                for (String chunkObject : chunkObjects) {
                    try (InputStream is = minioClient.getObject(
                            GetObjectArgs.builder().bucket(CHUNK_BUCKET).object(chunkObject).build()
                    )) {
                        is.transferTo(os);
                    }
                }
            }

            for (String chunkObject : chunkObjects) {
                minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(CHUNK_BUCKET).object(chunkObject).build()
                );
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
                "Failed to merge chunks for uploadId=" + uploadId +
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

    public List<Integer> getUploadedChunks(String uploadId) {
    String key = CHUNK_KEY_PREFIX + uploadId;
    return redisTemplate.<String, String>opsForHash()
            .keys(key).stream()
            .map(Integer::valueOf)
            .sorted()
            .collect(Collectors.toList());
    }
}
