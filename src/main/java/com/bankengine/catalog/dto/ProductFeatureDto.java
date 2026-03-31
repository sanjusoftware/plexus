package com.bankengine.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Feature component code is required")
    @Schema(example = "LOUNGE-ACCESS", description = "Unique code of the feature component.")
    private String featureComponentCode;

    @Schema(example = "Interest Rate", description = "Name of the feature (returned in response).")
    private String featureName;

    @Schema(example = "STRING", description = "Data type of the feature. Required only when creating a new feature on-the-fly.")
    private String dataType;

    @NotBlank(message = "Feature value cannot be blank")
    @Schema(example = "3.5", description = "Concrete value for this product feature.")
    private String featureValue;
}
