package com.pipeline.persistence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Business Event Emitter for S3 (Database Persistence).
 * Sends events to Dynatrace when DB save succeeds or fails.
 *
 * EVENT STRUCTURE (10 parameters total):
 * TOP-LEVEL (2 — visible on honeycomb tile):
 *   1. file_type  — FX / EDM / ACCOUNTS / GENERIC
 *   2. status     — SUCCESS / FAILED
 *
 * INSIDE data (8 — visible on click/drilldown):
 *   1. file_id        — Correlation ID
 *   2. stage          — S3_DB_PERSISTENCE
 *   3. stage_name     — Database Storage
 *   4. error_type     — Error classification
 *   5. error_detail   — Detailed error message
 *   6. file_size      — (not applicable for S3, set to 0)
 *   7. file_extension — (not applicable for S3, set to N/A)
 *   8. timestamp      — ISO timestamp
 */
@Service
public class BusinessEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(BusinessEventEmitter.class);

    @Value("${dynatrace.tenant.url}")
    private String dynatraceTenantUrl;

    @Value("${dynatrace.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate;

    private static final String BIZ_EVENTS_PATH = "/api/v2/bizevents/ingest";

    public BusinessEventEmitter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Emit a Business Event for Stage 3 (DB Persistence).
     *
     * @param fileId      Correlation ID
     * @param status      "SUCCESS" or "FAILED"
     * @param errorDetail Error description if failed, null if success
     * @param fileType    File type: FX, EDM, ACCOUNTS, or GENERIC
     */
    public void emitPersistenceEvent(String fileId, String status, String errorDetail, String fileType, long processingTimeMs) {

        Map<String, Object> event = new LinkedHashMap<>();

        // CloudEvents required fields
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-persistence-service");
        event.put("type", "com.pipeline.file.db_persistence");

        // --- TOP-LEVEL SUMMARY (2 fields — visible on honeycomb tile) ---
        event.put("file_type", fileType != null ? fileType : "GENERIC");
        event.put("status", status);

        // --- DETAIL DATA (8 fields — visible on click/drilldown) ---
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S3_DB_PERSISTENCE");
        data.put("stage_name", "Database Storage");
        data.put("error_type", errorDetail != null ? "DB_ERROR" : "NONE");
        data.put("error_detail", errorDetail != null ? errorDetail : "NONE");
        data.put("file_size", 0);
        data.put("file_extension", "N/A");
        data.put("timestamp", Instant.now().toString());
        data.put("processing_time_ms", processingTimeMs);
        event.put("data", data);

        sendToDynatrace(event);
    }

    private void sendToDynatrace(Map<String, Object> event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Api-Token " + apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            String url = dynatraceTenantUrl + BIZ_EVENTS_PATH;

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("[BIZ_EVENT] ✅ S3 event sent to Dynatrace | status={} | file_type={} | pipeline_status={}",
                    response.getStatusCode(), event.get("file_type"), event.get("status"));

        } catch (Exception e) {
            log.warn("[BIZ_EVENT_ERROR] Failed to send S3 event to Dynatrace: {}", e.getMessage());
        }
    }
}
