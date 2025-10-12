package com.streamvault.backend.repository;

import com.streamvault.backend.model.ChunkMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChunkMetadataRepository extends JpaRepository<ChunkMetadata, Long> {
    List<ChunkMetadata> findByUploadIdOrderByChunkNumber(String uploadId);
}
