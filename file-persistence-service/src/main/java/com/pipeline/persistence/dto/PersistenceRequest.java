package com.pipeline.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request received by S3 FROM S2.
 * Contains the already-parsed document sections ready for DB storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceRequest {

    private String fileId;          // Correlation ID
    private String fileName;
    private String fileType;        // FX, EDM, ACCOUNTS, or GENERIC
    private String title;           // From [TITLE]
    private String logoUrl;         // From [LOGO]
    private String content;         // From [CONTENT]
    private String footer;          // From [FOOTER]
    private String contentHash;     // Passed from S1 -> S2 -> S3
}
