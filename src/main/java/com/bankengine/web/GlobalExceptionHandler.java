package com.bankengine.web;

import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Helper method to create a consistent JSON error map
    private Map<String, Object> createErrorBody(HttpStatus status, String error, String message) {
        return Map.of(
            "timestamp", LocalDateTime.now(),
            "status", status.value(),
            "error", error,
            "message", message
        );
    }

    /**
     * 1. Handles validation errors from @Valid annotation. (400 BAD REQUEST)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        // Return a consistent JSON object structure
        Map<String, Object> body = createErrorBody(
            HttpStatus.BAD_REQUEST,
            "Validation Error",
            "Input validation failed."
        );
        body.put("details", errors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. Handles business logic errors (e.g., name conflict). (400 BAD REQUEST)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> body = createErrorBody(status, "Bad Request", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 3. Handles resource not found errors. (404 NOT FOUND)
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundException(NotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> body = createErrorBody(status, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 4. Handles business rule violations and internal state errors. (400 BAD REQUEST)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> body = createErrorBody(status, "Illegal State/Business Rule Violation", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 5. Handles dependency violation errors (409 CONFLICT)
     */
    @ExceptionHandler(DependencyViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyViolationException(DependencyViolationException ex) {
        HttpStatus status = HttpStatus.CONFLICT; // 409
        Map<String, Object> body = createErrorBody(status, "Conflict (Dependency Exists)", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }
}