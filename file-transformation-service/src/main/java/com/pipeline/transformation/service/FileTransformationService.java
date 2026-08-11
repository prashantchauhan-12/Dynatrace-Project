package com.pipeline.transformation.service;

import com.pipeline.transformation.dto.TransformationRequest;
import com.pipeline.transformation.dto.TransformedDocument;
import com.pipeline.transformation.exception.TransformationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║ STAGE 2 (S2) — FILE TRANSFORMATION SERVICE ║
 * ║ ║
 * ║ Responsibilities: ║
 * ║ 1. Parse file content into 4 sections: ║
 * ║ [TITLE], [LOGO], [CONTENT], [FOOTER] ║
 * ║ 2. Validate that all required sections exist ║
 * ║ 3. Emit Business Event to Dynatrace ║
 * ║ 4. Forward parsed document to Service 3 (Persistence) ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SIMULATED FILE FORMAT (for demo):
 * The file is a plain text file with section markers:
 *
 * [TITLE]
 * My Document Title
 *
 * [LOGO]
 * https://example.com/logo.png
 *
 * [CONTENT]
 * The actual body content goes here...
 *
 * [FOOTER]
 * Footer text here
 */
@Service
public class FileTransformationService {

    private static final Logger log = LoggerFactory.getLogger(FileTransformationService.class);

    private final BusinessEventEmitter businessEventEmitter;
    private final RestTemplate restTemplate;

    @Value("${service.persistence.url}")
    private String persistenceServiceUrl;

    public FileTransformationService(BusinessEventEmitter businessEventEmitter,
            RestTemplate restTemplate) {
        this.businessEventEmitter = businessEventEmitter;
        this.restTemplate = restTemplate;
    }

    /**
     * Transform the raw file content by parsing sections.
     *
     * @param request Contains fileId and raw file content from S1
     */
    public void transformFile(TransformationRequest request) {

        String fileId = request.getFileId();
        String fileType = request.getFileType() != null ? request.getFileType() : "GENERIC";
        log.info("[S2_TRANSFORMATION] ▶ Processing started | file_id={} | file_type={}", fileId, fileType);

        TransformedDocument document = null;

        try {
            String rawContent = request.getFileContent();

            // ─── Parse each section from the raw text ───
            String title = extractSection(rawContent, "TITLE", fileId);
            String logoUrl = extractSection(rawContent, "LOGO", fileId);
            String content = extractSection(rawContent, "CONTENT", fileId);
            String footer = extractSection(rawContent, "FOOTER", fileId);

            // ─── Validate required sections ───
            if (title == null || title.isBlank()) {
                throw new TransformationException(
                        "missing_section=\"Title\" file_id=\"" + fileId + "\"");
            }
            if (content == null || content.isBlank()) {
                throw new TransformationException(
                        "missing_section=\"Content\" file_id=\"" + fileId + "\"");
            }
            if (footer == null || footer.isBlank()) {
                // This is the exact log pattern Dynatrace should capture
                log.error("[TRANSFORMATION_ERROR] missing_section=\"Footer\" file_id=\"{}\"", fileId);
                throw new TransformationException(
                        "missing_section=\"Footer\" file_id=\"" + fileId + "\"");
            }

            log.info("[S2_TRANSFORMATION] ✅ All sections parsed | file_id={} | title='{}'",
                    fileId, title.substring(0, Math.min(title.length(), 50)));

            // ─── Build transformed document ───
            document = TransformedDocument.builder()
                    .fileId(fileId)
                    .fileName(request.getFileName())
                    .fileType(fileType)
                    .title(title)
                    .logoUrl(logoUrl != null ? logoUrl : "N/A")
                    .content(content)
                    .footer(footer)
                    .contentHash(request.getContentHash())
                    .build();

            // ─── Emit SUCCESS Business Event ───
            businessEventEmitter.emitTransformationEvent(fileId, "SUCCESS", null, fileType);

        } catch (TransformationException e) {
            log.error("[S2_ERROR] file_id={} | error={}", fileId, e.getMessage());

            // Emit FAILURE Business Event
            businessEventEmitter.emitTransformationEvent(fileId, "FAILED", e.getMessage(), fileType);
            throw e;

        } catch (Exception e) {
            log.error("[S2_ERROR] file_id={} | unexpected_error={}", fileId, e.getMessage(), e);

            businessEventEmitter.emitTransformationEvent(fileId, "FAILED", "UNEXPECTED: " + e.getMessage(), fileType);
            throw e;
        }

        // ─── Forward to Service 3 (Persistence) ───
        // We do this OUTSIDE the try-catch so that if S3 fails, S2 doesn't incorrectly
        // send a "FAILED" transformation event.
        forwardToPersistenceService(fileId, document);
    }

    /**
     * Parse a section from the raw file content.
     *
     * Example: Given marker "TITLE" and content:
     * [TITLE]
     * My Title Here
     * [LOGO]
     * ...
     *
     * This returns "My Title Here"
     */
    private String extractSection(String rawContent, String sectionName, String fileId) {
        String marker = "[" + sectionName + "]";

        int startIndex = rawContent.indexOf(marker);
        if (startIndex == -1) {
            log.warn("[S2_PARSER] Section '{}' not found in file | file_id={}", sectionName, fileId);
            return null;
        }

        // Move past the marker line
        int contentStart = rawContent.indexOf("\n", startIndex);
        if (contentStart == -1) {
            return null;
        }
        contentStart++; // Skip the newline character

        // Find the next section marker (starts with "[")
        int contentEnd = rawContent.length();
        int nextMarker = rawContent.indexOf("\n[", contentStart);
        if (nextMarker != -1) {
            contentEnd = nextMarker;
        }

        String extracted = rawContent.substring(contentStart, contentEnd).trim();
        log.debug("[S2_PARSER] Extracted section '{}': '{}...' | file_id={}",
                sectionName, extracted.substring(0, Math.min(extracted.length(), 30)), fileId);

        return extracted;
    }

    /**
     * Send the transformed document to Service 3 via HTTP POST.
     * This is the S2 → S3 inter-service communication.
     */
    private void forwardToPersistenceService(String fileId, TransformedDocument document) {
        log.info("[S2_TRANSFORMATION] 📤 Forwarding to S3 (Persistence) | file_id={}", fileId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-Id", fileId);
        headers.set("X-File-Type", document.getFileType() != null ? document.getFileType() : "GENERIC");

        HttpEntity<TransformedDocument> request = new HttpEntity<>(document, headers);

        try {
            restTemplate.postForEntity(persistenceServiceUrl, request, String.class);
            log.info("[S2_TRANSFORMATION] ✅ Successfully forwarded to S3 | file_id={}", fileId);
        } catch (Exception e) {
            log.error("[S2_ERROR] Failed to forward to S3 | file_id={} | error={}",
                    fileId, e.getMessage());
            throw new RuntimeException("Failed to reach Persistence Service: " + e.getMessage(), e);
        }
    }
}
