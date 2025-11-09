package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.dto.UploadStatusResponse;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.service.ChunkUploadService;
import com.streamvault.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final ChunkUploadService chunkUploadService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        // Validate file presence
        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new FileUploadResponse(null, null, null, 0, null, "No file provided"));
        }

        try {
            FileEntity savedFile = fileService.saveFileWithHash(file);
            return ResponseEntity.ok(new FileUploadResponse(
                    savedFile.getId(),
                    savedFile.getFileName(),
                    savedFile.getFileType(),
                    savedFile.getSize(),
                    savedFile.getHash(),
                    "File uploaded successfully"
            ));
        } catch (FileAlreadyExistsException e) {
            return ResponseEntity.badRequest()
                    .body(new FileUploadResponse(null, file.getOriginalFilename(), file.getContentType(), file.getSize(), null, e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<UploadStatusResponse> getUploadStatus(@RequestParam("uploadId") String uploadId) {
        UploadStatus status = chunkUploadService.getUploadStatus(uploadId);

        return ResponseEntity.ok(
            new UploadStatusResponse(
                uploadId,
                status.getStatus(),
                status.getFileName()
            )
        );
    }
}
