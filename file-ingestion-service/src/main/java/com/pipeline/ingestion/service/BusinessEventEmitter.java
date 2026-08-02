package com.pipeline.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  BUSINESS EVENT EMITTER — S1 (File Ingestion)              ║
 * ║                                                              ║
 * ║  This class sends Business Events to Dynatrace every time   ║
 * ║  a file is received (or fails validation).                   ║
 * ║                                                              ║
 * ║  API: POST {tenant}/api/v2/bizevents/ingest                 ║
 * ║  Docs: https://docs.dynatrace.com/docs/shortlink/ba-api    ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * HOW IT WORKS:
 * 1. Each Business Event follows the CloudEvents specification
 * 2. The event contains:
 *    - specversion, id, source, type (required CloudEvents fields)
 *    - data (your custom business payload)
 * 3. Dynatrace ingests these and makes them queryable via DQL:
 *    fetch bizevents | filter type == "com.pipeline.file.ingestion"
 */
@Service
public class BusinessEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(BusinessEventEmitter.class);

    @Value("${dynatrace.tenant.url}")
    private String dynatraceTenantUrl;

    @Value("${dynatrace.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate;

    // Dynatrace Business Events Ingest endpoint
    private static final String BIZ_EVENTS_PATH = "/api/v2/bizevents/ingest";

    public BusinessEventEmitter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Emit a Business Event for Stage 1 (File Ingestion).
     *
     * @param fileId        Correlation ID linking all 3 stages
     * @param status        "SUCCESS" or "FAILED"
     * @param errorType     Error type if failed (e.g., "INVALID_FILE_FORMAT"), null if success
     * @param fileSize      Size of the uploaded file in bytes
     * @param fileExtension File extension (e.g., "txt", "pdf")
     */
    public void emitIngestionEvent(String fileId, String status, String errorType,
                                    long fileSize, String fileExtension) {

        // Build the CloudEvents-compliant event
        Map<String, Object> event = new LinkedHashMap<>();

        // --- Required CloudEvents fields ---
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-ingestion-service");                 // Which microservice sent this
        event.put("type", "com.pipeline.file.ingestion");              // Event type identifier

        // --- Your custom business data ---
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S1_INGESTION");
        data.put("stage_name", "File Ingestion");
        data.put("status", status);
        data.put("error_type", errorType != null ? errorType : "NONE");
        data.put("file_size", fileSize);
        data.put("file_extension", fileExtension);
        data.put("timestamp", Instant.now().toString());
        event.put("data", data);

        // Send to Dynatrace
        sendToDynatrace(event);
    }

    /**
     * POST the event to Dynatrace Business Events Ingest API.
     * 
     * IMPORTANT: We catch all exceptions here so that Dynatrace 
     * communication failures NEVER break the actual pipeline.
     */
    private void sendToDynatrace(Map<String, Object> event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Api-Token " + apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            String url = dynatraceTenantUrl + BIZ_EVENTS_PATH;

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("[BIZ_EVENT] S1 event sent to Dynatrace. Status: {}. file_id={}, status={}",
                    response.getStatusCode(), event.get("data"), event.get("type"));

        } catch (Exception e) {
            // Log the error but DON'T throw — pipeline must continue even if Dynatrace is down
            log.warn("[BIZ_EVENT_ERROR] Failed to send S1 event to Dynatrace: {}. " +
                     "This does NOT affect file processing.", e.getMessage());
        }
    }
}
