package com.pipeline.transformation.exception;

/**
 * Thrown when file content parsing fails at S2.
 * Examples:
 *  - Missing [TITLE] section
 *  - Missing [CONTENT] section
 *  - Missing [FOOTER] section
 */
public class TransformationException extends RuntimeException {
    public TransformationException(String message) {
        super(message);
    }
}
