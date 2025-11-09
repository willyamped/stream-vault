package com.streamvault.backend.repository;

import com.streamvault.backend.model.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadStatusRepository extends JpaRepository<UploadStatus, Long> {
    Optional<UploadStatus> findByUploadId(String uploadId);
}
