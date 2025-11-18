package com.bankengine.web;

import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
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

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        });

        Map<String, Object> body = new HashMap<>();

        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Input validation failed: One or more fields contain invalid data.");
        body.put("details", fieldErrors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. Handles generic bad request errors (e.g., from service layer checks). (400 BAD REQUEST)
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
     * 4. Handles business rule violations that indicate an improper state transition. (400 BAD REQUEST)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        // Keeping this as BAD_REQUEST for general business logic issues not covered by specific exceptions
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> body = createErrorBody(status, "Illegal State/Business Rule Violation", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 5. Handles dependency violation errors (409 CONFLICT)
     * This is used when attempting to delete a resource that is actively referenced by another resource.
     */
    @ExceptionHandler(DependencyViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyViolationException(DependencyViolationException ex) {
        HttpStatus status = HttpStatus.CONFLICT; // 409
        Map<String, Object> body = createErrorBody(status, "Conflict (Dependency Exists)", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 6. Handles database unique constraint violations. (409 CONFLICT)
     * This catches errors where an entity with a duplicate unique key is saved to the database.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Data integrity conflict: The resource could not be saved because a unique constraint was violated (e.g., duplicate unique name or key).";
        HttpStatus status = HttpStatus.CONFLICT; // 409
        Map<String, Object> body = createErrorBody(status, "Conflict (Data Integrity)", message);

        return new ResponseEntity<>(body, status);
    }
}