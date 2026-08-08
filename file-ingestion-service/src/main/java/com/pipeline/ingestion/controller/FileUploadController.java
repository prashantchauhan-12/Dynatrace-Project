package com.pipeline.ingestion.controller;

import com.pipeline.ingestion.dto.FileUploadResponse;
import com.pipeline.ingestion.service.FileIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║ FILE UPLOAD CONTROLLER — REST API Entry Point ║
 * ║ ║
 * ║ Endpoint: POST /api/files/upload ║
 * ║ Input: Multipart file + optional X-Correlation-Id header ║
 * ║ Output: JSON with file_id and processing status ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * HOW TO TEST WITH POSTMAN:
 * 1. Method: POST
 * 2. URL: http://localhost:8081/api/files/upload
 * 3. Body → form-data → Key: "file" (type: File) → Value: select your file
 * 4. Headers → X-Correlation-Id: any-unique-id (optional)
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileIngestionService fileIngestionService;

    public FileUploadController(FileIngestionService fileIngestionService) {
        this.fileIngestionService = fileIngestionService;
    }

    /**
     * Upload a file for processing through the pipeline.
     *
     * @param file          The file to upload (multipart)
     * @param correlationId Optional correlation ID passed via header
     * @return JSON response with file_id and status
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "file_type", defaultValue = "GENERIC") String fileType,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.info("[S1_CONTROLLER] ▶ Received file upload request | name={} | size={} | correlationId={} | file_type={}",
                file.getOriginalFilename(), file.getSize(), correlationId, fileType);

        try {
            // Delegate to service layer for validation + processing
            String fileId = fileIngestionService.processFile(file, correlationId, fileType);

            return ResponseEntity.ok(FileUploadResponse.builder()
                    .fileId(fileId)
                    .status("RECEIVED")
                    .message("File successfully processed through all stages. Pipeline: S1 ✅ → S2 ✅ → S3 ✅")
                    .timestamp(LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            // Let GlobalExceptionHandler handle specific exceptions
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Health check endpoint — useful for verifying the service is running.
     * URL: GET http://localhost:8081/api/files/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("S1 File Ingestion Service is running ✅");
    }
}
