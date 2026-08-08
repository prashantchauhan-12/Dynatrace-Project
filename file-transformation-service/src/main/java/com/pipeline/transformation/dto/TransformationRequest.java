package com.pipeline.transformation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request received by S2 FROM S1.
 * Contains the raw file content and metadata for parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationRequest {

    private String fileId;          // Correlation ID from S1
    private String fileName;
    private long   fileSize;
    private String fileExtension;
    private String fileType;        // FX, EDM, ACCOUNTS, or GENERIC
    private String fileContent;     // Raw text content to parse
}
