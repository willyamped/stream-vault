package com.streamvault.backend.controller;

import com.streamvault.backend.dto.FileUploadResponse;
import com.streamvault.backend.model.FileEntity;
import com.streamvault.backend.service.ChunkUploadService;
import com.streamvault.backend.service.FileService;
import com.streamvault.backend.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {
    private final ChunkUploadService chunkService;
    private final FileService fileService;

    @PostMapping("/init")
    public ResponseEntity<String> initialiseUpload() {
        String uploadId = chunkService.initialiseUpload();
        return ResponseEntity.ok(uploadId);
    }

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }
        if (chunkNumber <= 0) {
            return ResponseEntity.badRequest().body("Invalid chunk number");
        }

        chunkService.saveChunk(uploadId, chunkNumber, file);
        return ResponseEntity.ok("Chunk " + chunkNumber + " uploaded");
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) {

        try {
            File mergedFile = chunkService.mergeChunks(uploadId, fileName);

            FileEntity savedFile = fileService.saveFile(
                    fileName,
                    Files.probeContentType(mergedFile.toPath()),
                    mergedFile.length(),
                    Util.computeHash(Files.readAllBytes(mergedFile.toPath()))
            );

            return ResponseEntity.ok(new FileUploadResponse(
                    savedFile.getId(),
                    savedFile.getFileName(),
                    savedFile.getFileType(),
                    savedFile.getSize(),
                    savedFile.getHash(),
                    "File uploaded and saved successfully"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing file");
        }
    }

}
