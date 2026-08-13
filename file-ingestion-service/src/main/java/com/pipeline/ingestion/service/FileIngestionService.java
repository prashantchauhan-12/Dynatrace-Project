package com.pipeline.ingestion.service;

import com.pipeline.ingestion.dto.IngestionPayload;
import com.pipeline.ingestion.exception.FileTooLargeException;
import com.pipeline.ingestion.exception.InvalidFileFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  STAGE 1 (S1) — FILE INGESTION SERVICE                     ║
 * ║                                                              ║
 * ║  Responsibilities:                                           ║
 * ║  1. Validate file format (only .txt, .csv, .pdf, .docx)     ║
 * ║  2. Validate file size (max 10MB)                            ║
 * ║  3. Generate correlation ID (file_id)                        ║
 * ║  4. Emit Business Event to Dynatrace                        ║
 * ║  5. Forward file to Service 2 (Transformation)               ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Service
public class FileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);

    private final BusinessEventEmitter businessEventEmitter;
    private final RestTemplate restTemplate;

    // Allowed file extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "csv", "pdf", "docx");

    // Max file size: 10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // URL of Service 2 (Transformation) — loaded from application.yml
    @Value("${service.transformation.url}")
    private String transformationServiceUrl;

    // URL of Service 3 (Persistence) — loaded from application.yml
    @Value("${service.persistence.url}")
    private String persistenceServiceUrl;

    public FileIngestionService(BusinessEventEmitter businessEventEmitter, RestTemplate restTemplate) {
        this.businessEventEmitter = businessEventEmitter;
        this.restTemplate = restTemplate;
    }

    /**
     * Process an uploaded file:
     * 1. Generate or use provided correlation ID
     * 2. Validate format and size
     * 3. Emit Dynatrace Business Event
     * 4. Forward to Service 2
     *
     * @param file          The uploaded file from Postman
     * @param correlationId Optional correlation ID from X-Correlation-Id header
     * @param fileType      File type: FX, EDM, ACCOUNTS, or GENERIC
     * @return The generated file_id
     */
    public String processFile(MultipartFile file, String correlationId, String fileType) throws IOException {
        long startTime = System.currentTimeMillis();

        // Compute SHA-256 Hash
        String contentHash = computeHash(file.getBytes());

        // Generate or version file_id
        String fileId = correlationId;
        if (fileId != null && !fileId.isBlank()) {
            try {
                String statusUrl = persistenceServiceUrl.replace("/persist", "/status/") + fileId;
                ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl, Map.class);
                Map<String, Object> body = response.getBody();
                if (body != null && Boolean.TRUE.equals(body.get("exists"))) {
                    String existingHash = (String) body.get("contentHash");
                    if (contentHash.equals(existingHash)) {
                        log.info("[S1_IDEMPOTENCY] ✅ Exact duplicate detected. Skipping pipeline for file_id={}", fileId);
                        return fileId;
                    } else {
                        log.warn("[S1_AUTO_VERSION] ⚠️ File content changed for file_id={}. Auto-versioning.", fileId);
                        fileId = fileId + "-v" + (System.currentTimeMillis() / 1000);
                    }
                }
            } catch (Exception e) {
                log.warn("[S1_IDEMPOTENCY] Failed to check status from S3: {}", e.getMessage());
            }
        } else {
            fileId = UUID.randomUUID().toString();
        }

        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        String extension = extractExtension(fileName);

        // Normalize fileType to uppercase
        String normalizedFileType = (fileType != null && !fileType.isBlank())
                ? fileType.toUpperCase()
                : "GENERIC";

        log.info("[S1_INGESTION] ▶ Processing started | file_id={} | name={} | size={} bytes | ext={} | file_type={}",
                fileId, fileName, fileSize, extension, normalizedFileType);

        // Step 2: Validate file FORMAT
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.error("[S1_ERROR] file_id={} | error_type=INVALID_FORMAT | extension={}",
                    fileId, extension);

            // Emit FAILURE event to Dynatrace
            businessEventEmitter.emitIngestionEvent(
                    fileId, "FAILED", "INVALID_FILE_FORMAT", fileSize, extension, normalizedFileType, System.currentTimeMillis() - startTime);

            throw new InvalidFileFormatException(
                    "File format '." + extension + "' is not supported. Allowed formats: " + ALLOWED_EXTENSIONS);
        }

        // Step 3: Validate file SIZE
        if (fileSize > MAX_FILE_SIZE) {
            log.error("[S1_ERROR] file_id={} | error_type=FILE_TOO_LARGE | size={} | max={}",
                    fileId, fileSize, MAX_FILE_SIZE);

            // Emit FAILURE event to Dynatrace
            businessEventEmitter.emitIngestionEvent(
                    fileId, "FAILED", "PAYLOAD_TOO_LARGE", fileSize, extension, normalizedFileType, System.currentTimeMillis() - startTime);

            throw new FileTooLargeException(
                    "File size (" + fileSize + " bytes) exceeds maximum allowed size (" + MAX_FILE_SIZE + " bytes)");
        }

        // Step 4: Emit SUCCESS event to Dynatrace
        businessEventEmitter.emitIngestionEvent(
                fileId, "SUCCESS", null, fileSize, extension, normalizedFileType, System.currentTimeMillis() - startTime);

        log.info("[S1_INGESTION] ✅ Validation passed | file_id={}", fileId);

        // Step 5: Forward to Service 2 (Transformation)
        forwardToTransformationService(fileId, file, fileName, fileSize, extension, normalizedFileType, contentHash);

        return fileId;
    }

    /**
     * Send the file content to Service 2 via HTTP POST.
     * This is the S1 → S2 inter-service communication.
     */
    private void forwardToTransformationService(String fileId, MultipartFile file,
                                                 String fileName, long fileSize,
                                                 String extension, String fileType,
                                                 String contentHash) throws IOException {
        log.info("[S1_INGESTION] 📤 Forwarding to S2 (Transformation) | file_id={} | file_type={}", fileId, fileType);

        // Read file content as string
        String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);

        // Build payload for Service 2
        IngestionPayload payload = IngestionPayload.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileExtension(extension)
                .fileType(fileType)
                .fileContent(fileContent)
                .contentHash(contentHash)
                .build();

        // Send HTTP POST to Service 2
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-Id", fileId);    // Propagate correlation ID in header
        headers.set("X-File-Type", fileType);        // Propagate file type in header

        HttpEntity<IngestionPayload> request = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(transformationServiceUrl, request, String.class);
            log.info("[S1_INGESTION] ✅ Successfully forwarded to S2 | file_id={}", fileId);
        } catch (Exception e) {
            log.error("[S1_ERROR] Failed to forward to S2 | file_id={} | error={}", fileId, e.getMessage());
            throw new RuntimeException("Failed to reach Transformation Service: " + e.getMessage(), e);
        }
    }

    /**
     * Extract file extension from filename.
     * Example: "report.pdf" → "pdf"
     */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(content);
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "UNKNOWN_HASH";
        }
    }
}
