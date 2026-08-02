package com.pipeline.transformation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload sent from S2 (Transformation) → S3 (Persistence).
 * Contains the parsed/extracted sections ready for database storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformedDocument {

    private String fileId;          // Correlation ID — same across all stages
    private String fileName;
    private String title;           // Extracted from [TITLE] section
    private String logoUrl;         // Extracted from [LOGO] section
    private String content;         // Extracted from [CONTENT] section
    private String footer;          // Extracted from [FOOTER] section
}
