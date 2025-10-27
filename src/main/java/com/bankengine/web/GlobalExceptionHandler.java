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

        // 1. Collect Field Errors into a clean, mutable HashMap
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        });

        // 2. Create the final response body structure using a mutable HashMap
        Map<String, Object> body = new HashMap<>();

        // Use standard HTTP status code and message
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");

        // Add custom high-level message for consistency
        body.put("message", "Input validation failed: One or more fields contain invalid data.");

        // 3. Include the detailed field errors
        // This maintains your consistent JSON object structure while providing details.
        body.put("details", fieldErrors);

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

    /**
     * 6. Handles Data Integrity Constraint violations (e.g., unique index violation on POST/PUT). (409 CONFLICT)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "A resource with the provided unique identifier (e.g., name, code) already exists.";

        // We can inspect the root cause for more detail if needed, but this generic message works for 409
//         String rootCause = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();

        HttpStatus status = HttpStatus.CONFLICT; // 409

        Map<String, Object> body = createErrorBody(status, "Conflict", message);

        return new ResponseEntity<>(body, status);
    }
}