package com.bankengine.web;

import com.bankengine.web.dto.ApiError;
import com.bankengine.web.dto.Violation;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.PriceValue;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. Handles JSR-303 validation errors and IllegalArgumentException.
     * Status: 400 Bad Request
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleValidationExceptions(Exception ex) {
        String message = "Input validation failed: One or more fields contain invalid data.";
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        if (ex instanceof MethodArgumentNotValidException methodEx) {
            methodEx.getBindingResult().getFieldErrors().forEach(error ->
                    violations.add(Violation.builder()
                            .field(error.getField())
                            .reason(error.getDefaultMessage())
                            .severity(Violation.Severity.ERROR)
                            .build())
            );
        } else {
            message = ex.getMessage();
        }

        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .code("VALIDATION_ERROR")
                .message(message)
                .errors(violations.isEmpty() ? null : violations)
                .build();

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 2. Handles custom business validation errors.
     * Status: 422 Unprocessable Entity
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleCustomValidationException(ValidationException ex) {
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Business Rule Violation")
                .code(ex.getCode())
                .message(ex.getMessage())
                .errors(ex.getViolations().isEmpty() ? null : ex.getViolations())
                .build();
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * 4. Handles resource not found errors.
     * Status: 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(NotFoundException ex) {
        return buildResponseEntity(HttpStatus.NOT_FOUND, "Not Found", "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    /**
     * 5. Handles improper state transitions.
     * Status: 409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null && (message.startsWith("Bank already exists: ") || message.startsWith("Bank ID already in use: "))) {
            message = "Bank ID should be unique";
        }
        return buildResponseEntity(HttpStatus.CONFLICT, "Illegal State", "CONFLICT", message);
    }

    /**
     * 6. Handles soft-dependency violations.
     * Status: 409 Conflict
     */
    @ExceptionHandler(DependencyViolationException.class)
    public ResponseEntity<ApiError> handleDependencyViolationException(DependencyViolationException ex) {
        return buildResponseEntity(HttpStatus.CONFLICT, "Conflict (Dependency)", "DEPENDENCY_VIOLATION", ex.getMessage());
    }

    /**
     * 7. Handles database unique constraint violations.
     * Status: 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage().toLowerCase() : "";
        String message = rootMsg;

        if (rootMsg.contains("uk_issuer_client_combination")) {
            message = "A bank with this Issuer URL and Client ID combination already exists.";
        } else if (rootMsg.contains("uk_bank_id")) {
            message = "Bank ID should be unique";
        }

        return buildResponseEntity(HttpStatus.CONFLICT, "Conflict", "DATA_INTEGRITY_VIOLATION", message);
    }

    /**
     * 8. Handles JSON parsing errors.
     * Status: 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        String lowerCaseMessage = message != null ? message.toLowerCase() : "";

        if (message != null && message.contains("com.bankengine.catalog.model.FeatureComponent$DataType")) {
            message = "Invalid data type provided. Valid values are: STRING, DECIMAL, INTEGER, BOOLEAN, DATE.";
        } else if (message != null && message.contains("com.bankengine.pricing.model.PricingComponent$ComponentType")) {
            message = "Invalid value for pricing component type. Valid values are " + enumValues(PricingComponent.ComponentType.values());
        } else if (message != null && message.contains("com.bankengine.pricing.model.PriceValue$ValueType")) {
            message = "Invalid value for price value type. Valid values are " + enumValues(PriceValue.ValueType.values());
        } else if (lowerCaseMessage.contains("boolean")) {
            if (lowerCaseMessage.contains("allowproductinmultiplebundles")) {
                message = "Invalid value for Boolean allowProductInMultipleBundles. Should be true or false";
            } else {
                message = "Invalid value for Boolean. Should be true or false";
            }
        }
        return buildResponseEntity(HttpStatus.BAD_REQUEST, "Bad Request", "MALFORMED_JSON", message);
    }

    private String enumValues(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }

    /**
     * 9. Handles Hibernate/JPA constraint violations.
     * Status: 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException ex) {
        return buildResponseEntity(HttpStatus.BAD_REQUEST, "Bad Request", "CONSTRAINT_VIOLATION", ex.getMessage());
    }

    /**
     * Helper method to maintain a consistent API error structure.
     */
    private ResponseEntity<ApiError> buildResponseEntity(HttpStatus status, String error, String code, String message) {
        ApiError body = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .code(code)
                .message(message)
                .build();
        return new ResponseEntity<>(body, status);
    }
}
