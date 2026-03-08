package com.bankengine.web;

import com.bankengine.web.dto.ApiError;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. Handles JSR-303 validation errors and IllegalArgumentException.
     * Status: 400 Bad Request
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleValidationExceptions(Exception ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        String message = "Input validation failed: One or more fields contain invalid data.";

        if (ex instanceof MethodArgumentNotValidException methodEx) {
            methodEx.getBindingResult().getFieldErrors().forEach(error ->
                    fieldErrors.put(error.getField(), error.getDefaultMessage())
            );
        } else {
            message = ex.getMessage();
        }

        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .details(fieldErrors.isEmpty() ? null : fieldErrors)
                .build();

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. Handles custom business validation errors.
     * Status: 422 Unprocessable Entity
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleCustomValidationException(ValidationException ex) {
        return buildResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY, "Business Rule Violation", ex.getMessage());
    }

    /**
     * 4. Handles resource not found errors.
     * Status: 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(NotFoundException ex) {
        return buildResponseEntity(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    /**
     * 5. Handles improper state transitions.
     * Status: 409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException ex) {
        return buildResponseEntity(HttpStatus.CONFLICT, "Illegal State", ex.getMessage());
    }

    /**
     * 6. Handles soft-dependency violations.
     * Status: 409 Conflict
     */
    @ExceptionHandler(DependencyViolationException.class)
    public ResponseEntity<ApiError> handleDependencyViolationException(DependencyViolationException ex) {
        return buildResponseEntity(HttpStatus.CONFLICT, "Conflict (Dependency)", ex.getMessage());
    }

    /**
     * 7. Handles database unique constraint violations.
     * Status: 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage().toLowerCase() : "";
        return buildResponseEntity(HttpStatus.CONFLICT, "Conflict", rootMsg);
    }

    /**
     * 8. Handles JSON parsing errors.
     * Status: 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return buildResponseEntity(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    /**
     * 9. Handles Hibernate/JPA constraint violations.
     * Status: 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException ex) {
        return buildResponseEntity(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    /**
     * Helper method to maintain a consistent API error structure.
     */
    private ResponseEntity<ApiError> buildResponseEntity(HttpStatus status, String error, String message) {
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .build();
        return new ResponseEntity<>(body, status);
    }
}
