package com.pipeline.persistence.service;

import com.pipeline.persistence.dto.PersistenceRequest;
import com.pipeline.persistence.model.FileDocument;
import com.pipeline.persistence.repository.FileDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  STAGE 3 (S3) — DATABASE PERSISTENCE SERVICE               ║
 * ║                                                              ║
 * ║  Responsibilities:                                           ║
 * ║  1. Receive transformed document from S2                    ║
 * ║  2. Save it to PostgreSQL                                    ║
 * ║  3. Emit Business Event to Dynatrace                        ║
 * ║  4. Handle DB errors (connection timeout, auth failure)      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * ERRORS TRACKED AT THIS STAGE:
 * - DB_CONNECTION_ERROR: PostgreSQL is down or connection timeout
 * - DB_AUTH_FAILURE:     Wrong username/password for PostgreSQL
 * - DUPLICATE_FILE:      file_id already exists in database
 * - UNEXPECTED:          Any other unexpected error
 */
@Service
public class DatabasePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DatabasePersistenceService.class);

    private final FileDocumentRepository repository;
    private final BusinessEventEmitter businessEventEmitter;

    public DatabasePersistenceService(FileDocumentRepository repository,
                                       BusinessEventEmitter businessEventEmitter) {
        this.repository = repository;
        this.businessEventEmitter = businessEventEmitter;
    }

    /**
     * Save the transformed document to PostgreSQL.
     *
     * @param request Contains fileId and parsed sections from S2
     */
    @Transactional
    public void saveDocument(PersistenceRequest request) {
        long startTime = System.currentTimeMillis();

        String fileId = request.getFileId();
        String fileType = request.getFileType() != null ? request.getFileType() : "GENERIC";
        log.info("[S3_DB_PERSIST] ▶ Starting database save | file_id={} | file_type={}", fileId, fileType);

        try {
            // Check for duplicate
            if (repository.existsByFileId(fileId)) {
                log.warn("[S3_WARNING] file_id={} already exists in database, skipping", fileId);
                businessEventEmitter.emitPersistenceEvent(fileId, "FAILED", "DUPLICATE_FILE", fileType, System.currentTimeMillis() - startTime);
                throw new RuntimeException("File with ID '" + fileId + "' already exists in database");
            }

            // Build JPA entity from the request
            FileDocument document = FileDocument.builder()
                    .fileId(fileId)
                    .fileName(request.getFileName())
                    .fileType(fileType)
                    .title(request.getTitle())
                    .logoUrl(request.getLogoUrl())
                    .content(request.getContent())
                    .footer(request.getFooter())
                    .contentHash(request.getContentHash())
                    .status("SUCCESS")
                    .build();

            // Save to PostgreSQL
            FileDocument saved = repository.save(document);

            log.info("[S3_DB_PERSIST] ✅ Saved to database | file_id={} | db_id={}",
                    fileId, saved.getId());

            // Emit SUCCESS Business Event to Dynatrace
            businessEventEmitter.emitPersistenceEvent(fileId, "SUCCESS", null, fileType, System.currentTimeMillis() - startTime);

        } catch (DataAccessException e) {
            // This catches:
            // - java.sql.SQLException
            // - Connection refused (DB down)
            // - Connection timeout
            // - Authentication failure
            log.error("[S3_ERROR] file_id={} | error_type=DB_CONNECTION | detail={}",
                    fileId, e.getMessage());

            // Check if it's specifically an auth failure
            String errorType = "DB_CONNECTION_ERROR";
            if (e.getMessage() != null &&
                (e.getMessage().contains("authentication") ||
                 e.getMessage().contains("password") ||
                 e.getMessage().contains("FATAL"))) {
                errorType = "DB_AUTH_FAILURE";
                log.error("[S3_ERROR] ⚠️ DATABASE AUTHENTICATION FAILURE | file_id={}", fileId);
            }

            businessEventEmitter.emitPersistenceEvent(fileId, "FAILED", errorType, fileType, System.currentTimeMillis() - startTime);
            throw e;

        } catch (RuntimeException e) {
            if (!e.getMessage().contains("already exists")) {
                log.error("[S3_ERROR] file_id={} | error_type=UNEXPECTED | detail={}",
                        fileId, e.getMessage(), e);
                businessEventEmitter.emitPersistenceEvent(
                        fileId, "FAILED", "UNEXPECTED: " + e.getMessage(), fileType, System.currentTimeMillis() - startTime);
            }
            throw e;

        } catch (Exception e) {
            log.error("[S3_ERROR] file_id={} | error_type=UNEXPECTED | detail={}",
                    fileId, e.getMessage(), e);
            businessEventEmitter.emitPersistenceEvent(
                    fileId, "FAILED", "UNEXPECTED: " + e.getMessage(), fileType, System.currentTimeMillis() - startTime);
            throw new RuntimeException(e);
        }
    }

    public java.util.Optional<FileDocument> findByFileId(String fileId) {
        return repository.findByFileId(fileId);
    }
}
