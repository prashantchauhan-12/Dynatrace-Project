package com.pipeline.ingestion.exception;

import com.pipeline.ingestion.dto.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

/**
 * Catches all exceptions thrown by S1 controllers and returns
 * clean JSON error responses (instead of ugly Spring default errors).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidFileFormatException.class)
    public ResponseEntity<FileUploadResponse> handleInvalidFormat(InvalidFileFormatException ex) {
        log.error("[S1_ERROR] Invalid file format: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(FileUploadResponse.builder()
                        .status("FAILED")
                        .error(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<FileUploadResponse> handleFileTooLarge(FileTooLargeException ex) {
        log.error("[S1_ERROR] File too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(FileUploadResponse.builder()
                        .status("FAILED")
                        .error(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // Spring's built-in multipart size exceeded exception
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<FileUploadResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.error("[S1_ERROR] Max upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(FileUploadResponse.builder()
                        .status("FAILED")
                        .error("File exceeds maximum upload size of 10MB")
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FileUploadResponse> handleGeneral(Exception ex) {
        log.error("[S1_ERROR] Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileUploadResponse.builder()
                        .status("FAILED")
                        .error("Internal server error: " + ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
