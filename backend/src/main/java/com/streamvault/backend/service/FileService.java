package com.streamvault.backend.service;

import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.repository.FileRepository;
import com.streamvault.backend.util.Util;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final MinioClient minioClient;
    private static final String BUCKET = "bucket";

    public FileEntity saveFile(String fileName, String fileType, Long size, String hash, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET)
                            .object(fileName)
                            .stream(fis, file.length(), -1)
                            .contentType(fileType)
                            .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to upload to MinIO" + e.getMessage());
        }

        return fileRepository.save(FileEntity.builder()
                .fileName(fileName)
                .fileType(fileType)
                .size(size)
                .hash(hash)
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    public FileEntity saveFileWithHash(MultipartFile multipartFile) throws IOException {
        String hash = Util.computeHash(multipartFile.getBytes());

        if (fileExists(hash)) {
            throw new FileAlreadyExistsException("File already exists with hash: " + hash);
        }

        File tempFile = Files.createTempFile("upload-", multipartFile.getOriginalFilename()).toFile();
        multipartFile.transferTo(tempFile);

        FileEntity savedFile = saveFile(
                multipartFile.getOriginalFilename(),
                multipartFile.getContentType(),
                multipartFile.getSize(),
                hash,
                tempFile
        );

        tempFile.delete();
        return savedFile;
    }

    public boolean fileExists(String hash) {
        return fileRepository.findByHash(hash).isPresent();
    }
}
