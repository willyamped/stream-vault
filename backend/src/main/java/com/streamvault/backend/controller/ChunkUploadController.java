package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.kafka.FileUploadProducer;
import com.streamvault.backend.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkService;
    private final FileUploadProducer fileUploadProducer;

    @PostMapping("/init")
    public ResponseEntity<String> initialiseUpload() {
        String uploadId = chunkService.initialiseUpload();
        log.info("[POST /api/upload/init] Initialized upload with uploadId={}", uploadId);
        return ResponseEntity.ok(uploadId);
    }

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile file) {

        log.info("[POST /api/upload/chunk] Uploading chunk={} for uploadId={}", chunkNumber, uploadId);

        try {
            if (file == null || file.isEmpty()) {
                log.warn("[POST /api/upload/chunk] No file provided for uploadId={}", uploadId);
                return ResponseEntity.badRequest().body("No file provided");
            }
            if (chunkNumber <= 0) {
                log.warn("[POST /api/upload/chunk] Invalid chunkNumber={} for uploadId={}", chunkNumber, uploadId);
                return ResponseEntity.badRequest().body("Invalid chunk number");
            }

            boolean uploaded = chunkService.saveChunk(uploadId, chunkNumber, file);
            if (!uploaded) {
                log.info("[POST /api/upload/chunk] Chunk {} already exists for uploadId={}", chunkNumber, uploadId);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Chunk " + chunkNumber + " already uploaded");
            }
            log.info("[POST /api/upload/chunk] Successfully uploaded chunk={} for uploadId={}", chunkNumber, uploadId);

            return ResponseEntity.ok("Chunk " + chunkNumber + " uploaded");

        } catch (Exception e) {
            log.error("[POST /api/upload/chunk] Error uploading chunk={} for uploadId={}: {}", chunkNumber, uploadId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading chunk: " + e.getMessage());
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<FileUploadResponse> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) {

        log.info("[POST /api/upload/complete] Completing upload for uploadId={}, fileName={}", uploadId, fileName);

        try {
            chunkService.completeUpload(uploadId, fileName);

            log.info("[POST /api/upload/complete] Upload request queued successfully for uploadId={}", uploadId);

            return ResponseEntity.ok(new FileUploadResponse(
                    null, fileName, null, 0, null,
                    "Upload request received — processing asynchronously"
            ));
        } catch (Exception e) {
            log.error("[POST /api/upload/complete] Failed to queue upload for uploadId={}, fileName={}: {}", uploadId, fileName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(null, fileName, null, 0, null,
                            "Failed to queue file for processing: " + e.getMessage()));
        }
    }

}
