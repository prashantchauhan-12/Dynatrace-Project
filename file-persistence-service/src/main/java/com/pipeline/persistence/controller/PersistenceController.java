package com.pipeline.persistence.controller;

import com.pipeline.persistence.dto.PersistenceRequest;
import com.pipeline.persistence.service.DatabasePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  PERSISTENCE CONTROLLER                                      ║
 * ║                                                              ║
 * ║  Endpoint: POST /api/persist                                 ║
 * ║  Called by: Service 2 (Transformation) — NOT Postman directly║
 * ║  Input:    JSON with fileId, title, logoUrl, content, footer ║
 * ║  Output:   JSON with stage status                             ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@RestController
@RequestMapping("/api")
public class PersistenceController {

    private static final Logger log = LoggerFactory.getLogger(PersistenceController.class);

    private final DatabasePersistenceService persistenceService;

    public PersistenceController(DatabasePersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Receive transformed document from S2 and save to database.
     * S2 sends: POST http://localhost:8083/api/persist
     */
    @PostMapping("/persist")
    public ResponseEntity<Map<String, String>> persist(
            @RequestBody PersistenceRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.info("[S3_CONTROLLER] ▶ Received persistence request | file_id={} | correlation_id={}",
                request.getFileId(), correlationId);

        persistenceService.saveDocument(request);

        return ResponseEntity.ok(Map.of(
                "stage", "S3_DB_PERSISTENCE",
                "file_id", request.getFileId(),
                "status", "SAVED",
                "message", "File saved to database. Pipeline COMPLETE: S1 ✅ → S2 ✅ → S3 ✅"
        ));
    }

    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable String fileId) {
        return persistenceService.findByFileId(fileId)
                .map(doc -> ResponseEntity.ok(Map.of(
                        "exists", (Object)true,
                        "contentHash", (Object)doc.getContentHash()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("exists", (Object)false)));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("S3 Persistence Service is running ✅");
    }
}
