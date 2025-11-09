package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.dto.UploadStatusResponse;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.service.ChunkUploadService;
import com.streamvault.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final ChunkUploadService chunkUploadService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        log.info("[POST /api/files/upload] Received file upload request: fileName={}", file != null ? file.getOriginalFilename() : "null");

        // Validate file presence
        if (file == null || file.isEmpty()) {
            log.warn("[POST /api/files/upload] No file provided in request");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new FileUploadResponse(null, null, null, 0, null, "No file provided"));
        }

        try {
            FileEntity savedFile = fileService.saveFileWithHash(file);
            log.info("[POST /api/files/upload] File uploaded successfully: id={}, fileName={}", savedFile.getId(), savedFile.getFileName());
            return ResponseEntity.ok(new FileUploadResponse(
                    savedFile.getId(),
                    savedFile.getFileName(),
                    savedFile.getFileType(),
                    savedFile.getSize(),
                    savedFile.getHash(),
                    "File uploaded successfully"
            ));
        } catch (FileAlreadyExistsException e) {
            log.warn("[POST /api/files/upload] File already exists: fileName={}, message={}", file.getOriginalFilename(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new FileUploadResponse(null, file.getOriginalFilename(), file.getContentType(), file.getSize(), null, e.getMessage()));
        } catch (Exception e) {
            log.error("[POST /api/files/upload] Unexpected error uploading file: fileName={}, message={}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(null, file.getOriginalFilename(), file.getContentType(), file.getSize(), null, "Unexpected error: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<UploadStatusResponse> getUploadStatus(@RequestParam("uploadId") String uploadId) {
        log.info("[GET /api/files/status] Checking upload status for uploadId={}", uploadId);

        try {
            UploadStatus status = chunkUploadService.getUploadStatus(uploadId);
            log.info("[GET /api/files/status] Retrieved status for uploadId={}: {}", uploadId, status.getStatus());
            return ResponseEntity.ok(
                    new UploadStatusResponse(
                            uploadId,
                            status.getStatus(),
                            status.getFileName()
                    )
            );
        } catch (Exception e) {
            log.error("[GET /api/files/status] Failed to retrieve upload status for uploadId={}: {}", uploadId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadStatusResponse(uploadId, null, null));
        }
    }
}
