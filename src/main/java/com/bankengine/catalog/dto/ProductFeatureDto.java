package com.bankengine.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standardized request DTO for linking and syncing product features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feature link detail for a product.")
public class ProductFeatureDto {

    @NotNull(message = "Feature Component ID is required")
    @Schema(example = "10", description = "ID of the master feature component (e.g., Interest Rate).")
    private Long featureComponentId;

    @Schema(example = "Interest Rate", description = "Name of the feature (returned in response).")
    private String featureName;

    @NotBlank(message = "Feature value cannot be blank")
    @Schema(example = "3.5", description = "Concrete value for this product feature.")
    private String featureValue;
}