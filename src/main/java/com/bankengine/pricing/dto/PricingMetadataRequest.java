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

    // AttributeKey: Must be unique and is what business users select in pricing rules.
    @NotBlank(message = "Attribute key is required.")
    @Size(max = 50, message = "Attribute key must be less than 50 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Attribute key can only contain letters, numbers, and underscores.")
    @Schema(example = "customerSegment", description = "The rule attribute key users select in pricing tiers.")
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

    @Pattern(
            regexp = "(?i)^(FACT_FIELD|CUSTOM_ATTRIBUTE)$",
            message = "Source type must be one of: FACT_FIELD, CUSTOM_ATTRIBUTE."
    )
    @Schema(example = "CUSTOM_ATTRIBUTE", description = "Where the rule engine should read this attribute from.")
    private String sourceType;

    @Size(max = 100, message = "Source field must be less than 100 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Source field can only contain letters, numbers, and underscores.")
    @Schema(example = "customerSegment", description = "Underlying field name on the pricing fact or customAttributes map key. Defaults to attributeKey.")
    private String sourceField;
}
