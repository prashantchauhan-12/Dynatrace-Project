package com.pipeline.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response returned to the client (Postman) after file upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String        fileId;
    private String        status;      // "RECEIVED", "FAILED"
    private String        message;
    private String        error;       // null on success
    private LocalDateTime timestamp;
}
