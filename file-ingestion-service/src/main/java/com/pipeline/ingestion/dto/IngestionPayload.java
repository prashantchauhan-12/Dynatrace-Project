package com.pipeline.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload sent from S1 (Ingestion) → S2 (Transformation)
 * Contains the raw file content and metadata for transformation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionPayload {

    private String fileId;          // Correlation ID — ties all 3 stages together
    private String fileName;        // Original file name
    private long   fileSize;        // File size in bytes
    private String fileExtension;   // e.g., "txt", "csv"
    private String fileType;        // FX, EDM, ACCOUNTS, or GENERIC
    private String fileContent;     // Raw text content of the file
}
