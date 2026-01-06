package com.bankengine.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingMetadataDto {

    // --- Response Only Fields ---
    private Long id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // --- Shared Fields with Request Validation ---

    // AttributeKey: Must be unique, used internally (e.g., in DRLs). Typically snake_case.
    @NotBlank(message = "Attribute key is required.")
    @Size(max = 50, message = "Attribute key must be less than 50 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Attribute key can only contain letters, numbers, and underscores.")
    private String attributeKey;

    // DisplayName: User-friendly name for UI (e.g., "Customer Segment").
    @NotBlank(message = "Display name is required.")
    @Size(max = 100, message = "Display name must be less than 100 characters.")
    private String displayName;

    // DataType: Crucial for DRL compilation and condition validation (e.g., STRING, DECIMAL, DATE).
    @NotBlank(message = "Data type is required.")
    @Pattern(regexp = "^(STRING|DECIMAL|INTEGER|BOOLEAN|DATE)$", message = "Data type must be one of: STRING, DECIMAL, INTEGER, BOOLEAN, DATE.")
    private String dataType;
}