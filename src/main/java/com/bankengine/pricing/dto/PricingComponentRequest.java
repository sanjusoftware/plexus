package com.bankengine.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating or updating a reusable pricing component aggregate, including optional tiers.")
public class PricingComponentRequest {

    @NotBlank(message = "Component code is required.")
    @Schema(example = "MONTHLY_MAINTENANCE_FEE", description = "Unique code for the pricing component.")
    private String code;

    @NotBlank(message = "Component name is required.")
    @Schema(example = "Monthly Maintenance Fee", description = "Display name for the pricing component.")
    private String name;

    @NotBlank(message = "Component type is required (e.g., FEE, RATE).")
    @Schema(example = "FEE", description = "Pricing component type. Allowed values: FEE, INTEREST_RATE, WAIVER, BENEFIT, DISCOUNT, PACKAGE_FEE, TAX")
    private String type;

    @Schema(example = "Standard monthly account maintenance fee", description = "Detailed description of what this component represents.")
    private String description;

    @Schema(example = "true", description = "If true, the resulting fee/rate can be prorated based on dates such as enrollment date and effective period.")
    private boolean proRataApplicable;

    @Schema(description = "Optional activation date.")
    private LocalDate activationDate;

    @Schema(description = "Optional expiry date.")
    private LocalDate expiryDate;

    @Valid
    @Schema(description = "Optional list of pricing tiers. Each tier defines conditions, thresholds, evaluation priority, and the resulting price value.")
    private List<PricingTierRequest> pricingTiers;
}
