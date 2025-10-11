package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        // Validate file presence
        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new FileUploadResponse(null, null, null, 0, null, "No file provided"));
        }

        // Compute hash for deduplication
        String hash = this.computeHash(file.getBytes());
        if (fileService.fileExists(hash)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new FileUploadResponse(null, file.getOriginalFilename(), file.getContentType(), file.getSize(), hash, "File already exists"));
        }

        // Save file
        FileEntity savedFile = fileService.saveFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), hash);

        FileUploadResponse response = new FileUploadResponse(
                savedFile.getId(),
                savedFile.getFileName(),
                savedFile.getFileType(),
                savedFile.getSize(),
                savedFile.getHash(),
                "File uploaded successfully"
        );
        return ResponseEntity.ok(response);
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
