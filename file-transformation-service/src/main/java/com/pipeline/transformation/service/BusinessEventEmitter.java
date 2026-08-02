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
     *
     * @param fileId      Correlation ID
     * @param status      "SUCCESS" or "FAILED"
     * @param errorDetail Error description if failed, null if success
     */
    public void emitTransformationEvent(String fileId, String status, String errorDetail) {

        Map<String, Object> event = new LinkedHashMap<>();

        // CloudEvents required fields
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", "file-transformation-service");
        event.put("type", "com.pipeline.file.transformation");

        // Business data
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file_id", fileId);
        data.put("stage", "S2_TRANSFORMATION");
        data.put("stage_name", "File Transformation");
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

            restTemplate.postForEntity(url, request, String.class);
            log.info("[BIZ_EVENT] S2 event sent to Dynatrace | type={}", event.get("type"));

        } catch (Exception e) {
            log.warn("[BIZ_EVENT_ERROR] Failed to send S2 event to Dynatrace: {}", e.getMessage());
        }
    }
}
