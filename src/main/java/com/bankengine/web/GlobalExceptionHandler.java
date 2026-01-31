package com.bankengine.web;

import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
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

    private Map<String, Object> createErrorBody(HttpStatus status, String error, String message) {
        return Map.of(
            "timestamp", LocalDateTime.now(),
            "status", status.value(),
            "error", error,
            "message", message
        );
    }

    /**
     * 1. Handles JSR-303 validation errors (e.g., @NotBlank, @Size).
     * Status: 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Input validation failed: One or more fields contain invalid data.");
        body.put("details", fieldErrors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. Handles custom business validation errors (e.g., category compatibility conflicts).
     * Status: 422 Unprocessable Entity
     * This is chosen because the request is well-formed (valid JSON) but violates business logic.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCustomValidationException(ValidationException ex) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY; // 422
        Map<String, Object> body = createErrorBody(status, "Business Rule Violation", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 3. Handles basic programming/logic errors (e.g., passing a null where not allowed).
     * Status: 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> body = createErrorBody(status, "Bad Request", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 4. Handles resource not found errors.
     * Status: 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundException(NotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> body = createErrorBody(status, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 5. Handles improper state transitions (e.g., activating an already active bundle).
     * Status: 409 Conflict (often better than 400 for state conflicts)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> body = createErrorBody(status, "Illegal State", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 6. Handles soft-dependency violations (e.g., deleting a product used in a bundle).
     * Status: 409 Conflict
     */
    @ExceptionHandler(DependencyViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyViolationException(DependencyViolationException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> body = createErrorBody(status, "Conflict (Dependency)", ex.getMessage());
        return new ResponseEntity<>(body, status);
    }

    /**
     * 7. Handles database unique constraint violations.
     * Status: 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Data integrity conflict: Unique constraint violation (e.g., duplicate code or name).";
        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> body = createErrorBody(status, "Conflict (Data Integrity)", message);
        return new ResponseEntity<>(body, status);
    }
}