package com.pipeline.transformation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TransformationException.class)
    public ResponseEntity<Map<String, Object>> handleTransformationError(TransformationException ex) {
        log.error("[S2_ERROR] Transformation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "stage", "S2_TRANSFORMATION",
                        "status", "FAILED",
                        "error", ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("[S2_ERROR] Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "stage", "S2_TRANSFORMATION",
                        "status", "FAILED",
                        "error", "Internal server error: " + ex.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
