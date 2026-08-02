package com.pipeline.persistence.repository;

import com.pipeline.persistence.model.FileDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for FileDocument.
 * Spring auto-generates all CRUD operations — no SQL needed!
 */
@Repository
public interface FileDocumentRepository extends JpaRepository<FileDocument, Long> {

    /**
     * Find a document by its correlation ID.
     * Spring Data generates: SELECT * FROM file_documents WHERE file_id = ?
     */
    Optional<FileDocument> findByFileId(String fileId);

    /**
     * Check if a document with this file_id already exists.
     */
    boolean existsByFileId(String fileId);
}
