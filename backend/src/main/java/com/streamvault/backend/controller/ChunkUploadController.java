package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.kafka.FileUploadProducer;
import com.streamvault.backend.model.UploadStatus;
import com.streamvault.backend.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkService;
    private final FileUploadProducer fileUploadProducer;

    @PostMapping("/init")
    public ResponseEntity<String> initialiseUpload() {
        String uploadId = chunkService.initialiseUpload();
        return ResponseEntity.ok(uploadId);
    }

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile file) {

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file provided");
            }
            if (chunkNumber <= 0) {
                return ResponseEntity.badRequest().body("Invalid chunk number");
            }

            chunkService.saveChunk(uploadId, chunkNumber, file);
            return ResponseEntity.ok("Chunk " + chunkNumber + " uploaded");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading chunk: " + e.getMessage());
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<FileUploadResponse> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) {

        try {
            chunkService.completeUpload(uploadId, fileName);

            return ResponseEntity.ok(new FileUploadResponse(
                    null, fileName, null, 0, null,
                    "Upload request received — processing asynchronously"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(null, fileName, null, 0, null,
                            "Failed to queue file for processing: " + e.getMessage()));
        }
    }


}
