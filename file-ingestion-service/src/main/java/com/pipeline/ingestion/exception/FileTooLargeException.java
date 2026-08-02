package com.pipeline.ingestion.exception;

/**
 * Thrown when the uploaded file exceeds the maximum allowed size.
 * Default limit: 10MB (configured in application.yml)
 */
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(String message) {
        super(message);
    }
}
