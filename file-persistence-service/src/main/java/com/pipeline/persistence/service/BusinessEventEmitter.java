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
     */
    public void emitPersistenceEvent(String fileId, String status, String errorDetail) {

        Map<String, Object> event = new LinkedHashMap<>();

        // CloudEvents required fields
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-persistence-service");
        event.put("type", "com.pipeline.file.db_persistence");

        // Business data
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S3_DB_PERSISTENCE");
        data.put("stage_name", "Database Storage");
        data.put("status", status);
        data.put("error_detail", errorDetail != null ? errorDetail : "NONE");
        data.put("timestamp", Instant.now().toString());
        event.put("data", data);

        sendToDynatrace(event);
    }

    private void sendToDynatrace(Map<String, Object> event) {
        try {
            if (apiToken == null || apiToken.isBlank() || apiToken.contains("XXXXX")) {
                log.error("[BIZ_EVENT_ERROR] ❌ Dynatrace API token is NOT configured! Token value: '{}'. Events will NOT be sent.", apiToken);
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Api-Token " + apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            String url = dynatraceTenantUrl + BIZ_EVENTS_PATH;

            log.info("[BIZ_EVENT] Sending S3 event to Dynatrace | url={} | token_length={}", url, apiToken.length());
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("[BIZ_EVENT] ✅ S3 event sent to Dynatrace | status={} | type={}", response.getStatusCode(), event.get("type"));

        } catch (Exception e) {
            log.error("[BIZ_EVENT_ERROR] ❌ Failed to send S3 event to Dynatrace: {}", e.getMessage());
        }
    }
}
