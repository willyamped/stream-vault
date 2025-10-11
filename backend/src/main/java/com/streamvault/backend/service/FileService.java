package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;

    public FileEntity saveFile(String fileName, String fileType, Long size, String hash) {
        return fileRepository.save(FileEntity.builder()
                .fileName(fileName)
                .fileType(fileType)
                .size(size)
                .hash(hash)
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    public boolean fileExists(String hash) {
        return fileRepository.findByHash(hash).isPresent();
    }
}
