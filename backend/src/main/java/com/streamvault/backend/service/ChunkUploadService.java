package com.streamvault.backend.service;

import com.streamvault.backend.model.ChunkMetadata;
import com.streamvault.backend.repository.ChunkMetadataRepository;
import com.streamvault.backend.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChunkUploadService {
    private final ChunkMetadataRepository chunkMetadataRepository;
    private final FileService fileService;
    private static final String TEMP_DIR = "uploads/tmp";

    public String initialiseUpload() {
        return UUID.randomUUID().toString();
    }

    public void saveChunk(String uploadId, int chunkNumber, MultipartFile file) throws IOException {
        Path uploadDir = Paths.get(TEMP_DIR, uploadId);
        Files.createDirectories(uploadDir);

        Path chunkPath = uploadDir.resolve("chunk_" + chunkNumber);
        file.transferTo(chunkPath);

        ChunkMetadata savedMetaData = chunkMetadataRepository.save(ChunkMetadata.builder()
                .uploadId(uploadId)
                .chunkNumber(chunkNumber)
                .chunkPath(chunkPath.toString())
                .size(file.getSize())
                .build());
    }

    public File mergeChunks(String uploadId, String filename) throws IOException {
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByUploadIdOrderByChunkNumber(uploadId);
        Path finalFile = Paths.get(TEMP_DIR, filename);

        try (OutputStream os = Files.newOutputStream(finalFile)) {
            for (ChunkMetadata chunk : chunks) {
                Files.copy(Paths.get(chunk.getChunkPath()), os);
            }
        }

        byte[] fileBytes = Files.readAllBytes(finalFile);
        String hash = Util.computeHash(fileBytes);
        if (fileService.fileExists(hash)) {
            throw new IllegalStateException("File already exists with hash: " + hash);
        }
        return finalFile.toFile();
    }
}
