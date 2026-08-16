package com.pipeline.transformation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Business Event Emitter for S2 (Transformation).
 * Sends events to Dynatrace when transformation succeeds or fails.
 *
 * EVENT STRUCTURE (10 parameters total):
 * TOP-LEVEL (2 — visible on honeycomb tile):
 *   1. file_type  — FX / EDM / ACCOUNTS / GENERIC
 *   2. status     — SUCCESS / FAILED
 *
 * INSIDE data (8 — visible on click/drilldown):
 *   1. file_id        — Correlation ID
 *   2. stage          — S2_TRANSFORMATION
 *   3. stage_name     — File Transformation
 *   4. error_type     — Error classification
 *   5. error_detail   — Detailed error message
 *   6. file_size      — (not applicable for S2, set to 0)
 *   7. file_extension — (not applicable for S2, set to N/A)
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
     * Emit a Business Event for Stage 2 (Transformation).
     */
    public void emitTransformationEvent(String fileId, String status, String errorDetail, String fileType, long processingTimeMs) {

        Map<String, Object> event = new LinkedHashMap<>();

        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-transformation-service");
        event.put("type", "com.pipeline.file.transformation");

        event.put("file_type", fileType != null ? fileType : "GENERIC");
        event.put("status", status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S2_TRANSFORMATION");
        data.put("stage_name", "File Transformation");
        data.put("error_type", errorDetail != null ? "TRANSFORMATION_ERROR" : "NONE");
        data.put("error_detail", errorDetail != null ? errorDetail : "NONE");
        data.put("file_size", 0);
        data.put("file_extension", "N/A");
        data.put("timestamp", Instant.now().toString());
        data.put("processing_time_ms", processingTimeMs);
        event.put("data", data);

        sendToDynatrace(event);
    }

    /**
     * Emit a Business Event for Method-Level Auditing.
     * Tracks individual method execution time per file_id.
     */
    public void emitMethodAuditEvent(String fileId, String methodName, long processingTimeMs, String status, String errorDetail) {
        Map<String, Object> event = new LinkedHashMap<>();

        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-transformation-service");
        event.put("type", "com.pipeline.method.audit");

        event.put("method_name", methodName);
        event.put("status", status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S2_TRANSFORMATION");
        data.put("method_name", methodName);
        data.put("processing_time_ms", processingTimeMs);
        data.put("status", status);
        data.put("error_detail", errorDetail != null ? errorDetail : "NONE");
        data.put("timestamp", Instant.now().toString());

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
            log.info("[BIZ_EVENT] ✅ S2 event sent to Dynatrace | status={} | type={} | pipeline_status={}",
                    response.getStatusCode(), event.get("type"), event.get("status"));

        } catch (Exception e) {
            log.warn("[BIZ_EVENT_ERROR] Failed to send S2 event to Dynatrace: {}", e.getMessage());
        }
    }
}
