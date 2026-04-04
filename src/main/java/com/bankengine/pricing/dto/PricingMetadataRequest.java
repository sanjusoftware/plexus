package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request object for creating pricing input metadata.")
public class PricingMetadataRequest {

    // AttributeKey: Must be unique, used internally (e.g., in DRLs). Typically snake_case.
    @NotBlank(message = "Attribute key is required.")
    @Size(max = 50, message = "Attribute key must be less than 50 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Attribute key can only contain letters, numbers, and underscores.")
    @Schema(example = "income", description = "The internal key used in rules. Should be snake_case.")
    private String attributeKey;

    // DisplayName: User-friendly name for UI (e.g., "Customer Segment").
    @NotBlank(message = "Display name is required.")
    @Size(max = 100, message = "Display name must be less than 100 characters.")
    @Schema(example = "Annual Income", description = "Display name shown in the UI.")
    private String displayName;

    // DataType: Crucial for DRL compilation and condition validation (e.g., STRING, DECIMAL, DATE).
    @NotBlank(message = "Data type is required.")
    @Pattern(
            regexp = "(?i)^(STRING|DECIMAL|INTEGER|LONG|BOOLEAN|DATE)$",
            message = "Data type must be one of: STRING, DECIMAL, INTEGER, LONG, BOOLEAN, DATE."
    )
    @Schema(example = "DECIMAL", description = "The data type of the attribute. Allowed values: STRING, DECIMAL, INTEGER, LONG, BOOLEAN, DATE.")
    private String dataType;
}
