package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingComponentRequest {

    @NotBlank(message = "Component code is required.")
    @Schema(example = "MONTHLY_MAINTENANCE_FEE", description = "Unique code for the pricing component.")
    private String code;

    @NotBlank(message = "Component name is required.")
    @Schema(example = "Monthly Maintenance Fee", description = "Display name for the pricing component.")
    private String name;

    @NotBlank(message = "Component type is required (e.g., FEE, RATE).")
    @Schema(example = "FEE", description = "The type of pricing component. Allowed values: FEE, DISCOUNT, WAIVER, BENEFIT, INTEREST_RATE, PACKAGE_FEE, TAX")
    private String type;

    @Schema(example = "Standard monthly account maintenance fee", description = "Detailed description of what this component represents.")
    private String description;

    @Schema(example = "true", description = "Indicates if the fee should be calculated pro-rata based on the number of days the product was active in a period.")
    private boolean proRataApplicable;

    @Valid
    @Schema(description = "List of pricing tiers that define the rules and values for this component.")
    private List<PricingTierRequest> pricingTiers;
}
