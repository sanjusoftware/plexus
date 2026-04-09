package com.bankengine.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error payload returned for validation, lookup, and business rule failures.")
public class ApiError {
    @Schema(description = "Timestamp when the error response was generated.", example = "2026-04-04T09:15:30")
    private final LocalDateTime timestamp;

    @Schema(description = "HTTP status code.", example = "400")
    private final int status;

    @Schema(description = "Short HTTP error label.", example = "Bad Request")
    private final String error;

    @Schema(description = "Human-readable explanation of the failure.", example = "Input validation failed: One or more fields contain invalid data.")
    private final String message;

    @Schema(description = "Business-specific error code.", example = "BUSINESS_RULE_VIOLATION")
    private final String code;

    @Schema(description = "Request path that produced the error when available.", example = "/api/v1/pricing-components/100/tiers")
    private final String path;

    @Schema(description = "Optional field-level validation messages keyed by field name.")
    private final Map<String, String> details;

    @Schema(description = "Structured list of business rule violations.")
    private final java.util.List<Violation> errors;
}