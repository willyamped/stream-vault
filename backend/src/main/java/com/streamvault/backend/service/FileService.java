package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.repository.FileRepository;
import com.streamvault.backend.util.Util;
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
        String hash = Util.computeHash(file.getBytes());
        if (fileExists(hash)) {
            throw new FileAlreadyExistsException("File already exists");
        }
        return saveFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), hash);
    }


    public boolean fileExists(String hash) {
        return fileRepository.findByHash(hash).isPresent();
    }
}
