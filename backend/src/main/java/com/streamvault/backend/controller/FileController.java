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
import java.nio.file.FileAlreadyExistsException;
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
}
