package com.streamvault.backend.service;

import com.streamvault.backend.kafka.FileUploadProducer;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.model.VideoEntity;
import com.streamvault.backend.model.FileEntity.FileCategory;
import com.streamvault.backend.repository.UploadStatusRepository;
import com.streamvault.backend.search.VideoIndexService;
import com.streamvault.backend.util.Util;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FileService fileService;
    private final VideoService videoService;
    private final VideoIndexService videoIndexService;
    private final UploadStatusRepository uploadStatusRepository;
    private final FileUploadProducer fileUploadProducer;
    private final MinioClient minioClient;

    private static final String CHUNK_BUCKET = "upload-chunks";
    private static final String CHUNK_KEY_PREFIX = "upload:";
    private static final Duration UPLOAD_TTL = Duration.ofHours(24);

    private boolean isVideo(String fileName) {
        String ext = fileName.toLowerCase();
        return ext.endsWith(".mp4") || ext.endsWith(".mov") || ext.endsWith(".mkv");
    }

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
            log.info("[mergeChunks] Starting merge for uploadId={}, fileName={}", uploadId, fileName);

            // 1. Get chunk objects from Redis sorted by index
            List<String> chunkObjects = redisTemplate.<String, String>opsForHash()
                    .entries(key)
                    .entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            log.info("[mergeChunks] Found {} chunks in Redis for uploadId={}", chunkObjects.size(), uploadId);

            List<Path> chunkPaths = new ArrayList<>();
            for (String chunkObject : chunkObjects) {
                log.info("[mergeChunks] Downloading chunk {} from MinIO", chunkObject);
                Path tempChunk = Files.createTempFile(uploadId + "_chunk_", ".part");
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder().bucket(CHUNK_BUCKET).object(chunkObject).build());
                    OutputStream os = Files.newOutputStream(tempChunk)) {
                    is.transferTo(os);
                }
                chunkPaths.add(tempChunk);
            }

            // 2. Merge chunks
            Path finalFile;
            if (isVideo(fileName)) {
                log.info("[mergeChunks] File is video, preparing FFmpeg merge");
                Path concatFile = Files.createTempFile(uploadId + "_concat", ".txt");
                List<String> lines = chunkPaths.stream()
                        .map(p -> "file '" + p.toAbsolutePath().toString().replace("\\", "/") + "'")
                        .collect(Collectors.toList());
                Files.write(concatFile, lines, StandardOpenOption.WRITE);

                finalFile = Files.createTempFile(uploadId + "_merged", ".mp4");

                // Run FFmpeg
                log.info("[mergeChunks] Running FFmpeg for uploadId={}", uploadId);
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-f", "concat", "-safe", "0",
                    "-i", concatFile.toAbsolutePath().toString(),
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-strict", "experimental",
                    finalFile.toAbsolutePath().toString()
                );
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.error("[mergeChunks] FFmpeg merge failed with exitCode={} for uploadId={}", exitCode, uploadId);
                    throw new IllegalStateException("FFmpeg merge failed for uploadId=" + uploadId);
                }
                log.info("[mergeChunks] FFmpeg merge completed for uploadId={}", uploadId);

                Files.delete(concatFile);
            } else {
                log.info("[mergeChunks] Non-video file, performing simple merge");
                finalFile = Files.createTempFile(uploadId + "_merged", ".bin");
                try (OutputStream os = Files.newOutputStream(finalFile)) {
                    for (Path chunkPath : chunkPaths) {
                        Files.copy(chunkPath, os);
                    }
                }
            }

            // 3. Cleanup chunks from MinIO and Redis
            for (String chunkObject : chunkObjects) {
                log.info("[mergeChunks] Deleting chunk {} from MinIO", chunkObject);
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(CHUNK_BUCKET).object(chunkObject).build());
            }
            redisTemplate.delete(key);
            log.info("[mergeChunks] Deleted Redis key {}", key);

            // 4. Compute hash and check duplication
            byte[] fileBytes = Files.readAllBytes(finalFile);
            String hash = Util.computeHash(fileBytes);
            log.info("[mergeChunks] Computed hash {} for uploadId={}", hash, uploadId);

            if (fileService.fileExists(hash)) {
                log.warn("[mergeChunks] File already exists with hash={}", hash);
                throw new IllegalStateException("File already exists with hash: " + hash);
            }

            String contentType = Files.probeContentType(finalFile);
            if (contentType == null) contentType = "application/octet-stream";

            FileEntity savedFile = fileService.saveFile(
                    fileName,
                    contentType,
                    finalFile.toFile().length(),
                    hash,
                    finalFile.toFile()
            );
            log.info("[mergeChunks] Saved file {} with ID={}", fileName, savedFile.getId());
            VideoEntity savedVideo = null;

            // 5. Categorize file and trigger video service if needed
            if (isVideo(fileName)) {
                savedFile.setCategory(FileCategory.VIDEO);
                savedFile = fileService.save(savedFile);
                savedVideo = videoService.createPendingVideo(savedFile);
                log.info("[mergeChunks] Created pending video for file ID={}", savedFile.getId());
            } else {
                savedFile.setCategory(FileCategory.FILE);
                savedFile = fileService.save(savedFile);
            }

            status.setStatus(UploadStatus.Status.COMPLETED);
            uploadStatusRepository.save(status);
            log.info("[mergeChunks] Merge completed successfully for uploadId={}", uploadId);

            // 6. Cleanup temp files
            for (Path p : chunkPaths) Files.deleteIfExists(p);
            Files.deleteIfExists(finalFile);
            videoIndexService.indexVideo(savedVideo);

            return savedFile;

        } catch (Exception e) {
            log.error("[mergeChunks] Merge failed for uploadId={}, error={}", uploadId, e.getMessage(), e);
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
