package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.security.MessageDigest;
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

    public FileEntity saveFileWithHash(MultipartFile file) throws IOException {
        String hash = computeHash(file.getBytes());
        if (fileExists(hash)) {
            throw new FileAlreadyExistsException("File already exists");
        }
        return saveFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), hash);
    }


    public boolean fileExists(String hash) {
        return fileRepository.findByHash(hash).isPresent();
    }

    private String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
