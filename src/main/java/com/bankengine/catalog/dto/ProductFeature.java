package com.bankengine.catalog.dto;

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
public class ProductFeature {

    @NotNull(message = "Feature Component ID is required")
    private Long featureComponentId; // Used for Request

    private String featureName; // Used for Response

    @NotBlank(message = "Feature value cannot be blank")
    private String featureValue; // Used for both
}