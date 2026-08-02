package com.pipeline.transformation.controller;

import com.pipeline.transformation.dto.TransformationRequest;
import com.pipeline.transformation.service.FileTransformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  TRANSFORMATION CONTROLLER                                   ║
 * ║                                                              ║
 * ║  Endpoint: POST /api/transform                               ║
 * ║  Called by: Service 1 (Ingestion) — NOT by Postman directly  ║
 * ║  Input:    JSON with fileId, fileName, fileContent           ║
 * ║  Output:   JSON with stage status                             ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api")
public class TransformationController {

    private static final Logger log = LoggerFactory.getLogger(TransformationController.class);

    private final FileTransformationService transformationService;

    public TransformationController(FileTransformationService transformationService) {
        this.transformationService = transformationService;
    }

    /**
     * Receive file content from S1 and transform it.
     * S1 sends: POST http://localhost:8082/api/transform
     */
    @PostMapping("/transform")
    public ResponseEntity<Map<String, String>> transform(
            @RequestBody TransformationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.info("[S2_CONTROLLER] ▶ Received transformation request | file_id={} | correlation_id={}",
                request.getFileId(), correlationId);

        transformationService.transformFile(request);

        return ResponseEntity.ok(Map.of(
                "stage", "S2_TRANSFORMATION",
                "file_id", request.getFileId(),
                "status", "TRANSFORMED",
                "message", "File transformed and forwarded to S3. Pipeline: S1 ✅ → S2 ✅ → S3"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("S2 Transformation Service is running ✅");
    }
}
