package com.pipeline.ingestion.exception;

/**
 * Thrown when the uploaded file has an unsupported extension.
 * Example: user uploads a .exe file but only .txt, .csv, .pdf, .docx are allowed.
 */
public class InvalidFileFormatException extends RuntimeException {
    public InvalidFileFormatException(String message) {
        super(message);
    }
}
