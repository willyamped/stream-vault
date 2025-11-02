package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
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

    private static final String TEMP_DIR = "uploads/tmp";
    private static final String CHUNK_KEY_PREFIX = "upload:";

    public String initialiseUpload() {
        return UUID.randomUUID().toString();
    }

    public void saveChunk(String uploadId, int chunkNumber, MultipartFile file) throws IOException {
        Path uploadDir = Paths.get(TEMP_DIR, uploadId);
        Files.createDirectories(uploadDir);

        Path chunkPath = uploadDir.resolve("chunk_" + chunkNumber);
        file.transferTo(chunkPath);

        // store in Redis as hash: key = upload:<uploadId>, field = chunkNumber, value = chunkPath
        String key = CHUNK_KEY_PREFIX + uploadId;
        redisTemplate.opsForHash().put(key, String.valueOf(chunkNumber), chunkPath.toString());
    }

 File mergeChunks(String uploadId, String filename) throws IOException {
        String key = CHUNK_KEY_PREFIX + uploadId;

        // Retrieve chunk paths from Redis
        List<String> chunkPaths = redisTemplate.<String, String>opsForHash().entries(key)
                .entrySet().stream()
                .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                .map(e -> e.getValue())
                .collect(Collectors.toList());

        if (chunkPaths.isEmpty()) {
            throw new IllegalStateException("No chunks found for uploadId: " + uploadId);
        }

        Path finalFile = Paths.get(TEMP_DIR, filename);
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

        return finalFile.toFile();
    }

    public FileEntity mergeChunksAndSaveFile(String uploadId, String fileName) throws IOException {
        File mergedFile = mergeChunks(uploadId, fileName);

        String contentType = Files.probeContentType(mergedFile.toPath());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        FileEntity savedFile = fileService.saveFile(
                fileName,
                contentType,
                mergedFile.length(),
                Util.computeHash(Files.readAllBytes(mergedFile.toPath())),
                mergedFile
        );

        return savedFile;
    }
}
