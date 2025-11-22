package com.streamvault.backend.service;

import com.streamvault.backend.dto.StreamedFile;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.repository.FileRepository;
import com.streamvault.backend.util.Util;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final MinioClient minioClient;
    private static final String FILE_BUCKET = "files";

    public FileEntity save(FileEntity file) {
        return fileRepository.save(file);
    }

    public FileEntity saveFile(String fileName, String fileType, Long size, String hash, File file) throws IOException {
        String objectName = UUID.randomUUID() + "_" + fileName;
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(FILE_BUCKET)
                            .object(objectName)
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
                .minioPath(objectName)
                .bucket(FILE_BUCKET)
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

    public StreamedFile streamFile(FileEntity file, String rangeHeader) throws Exception {
        String bucket = file.getBucket();
        String objectName = file.getMinioPath();
        long fileSize = file.getSize();

        long start = 0;
        long end = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.replace("bytes=", "").split("-");
            start = Long.parseLong(parts[0]);

            if (parts.length > 1 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }
        }

        long contentLength = end - start + 1;

        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .offset(start)
                        .length(contentLength)
                        .build()
        )) {
            byte[] data = is.readAllBytes();

            return new StreamedFile(
                    data,
                    start,
                    end,
                    fileSize,
                    file.getFileType(),
                    rangeHeader != null // partial?
            );
        }
    }
}
