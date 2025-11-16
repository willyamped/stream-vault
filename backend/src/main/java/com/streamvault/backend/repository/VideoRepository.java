package com.streamvault.backend.repository;

import com.streamvault.backend.model.VideoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<VideoEntity, Long> {
}
