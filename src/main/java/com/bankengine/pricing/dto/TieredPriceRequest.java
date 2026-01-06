package com.bankengine.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified DTO for both creating and updating tiered pricing.
 * Combines the definition of the tier (e.g., 0-10k) with the price (e.g., $5 fee).
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TieredPriceRequest {
    @Valid
    @NotNull(message = "Pricing Tier data is required.")
    private PricingTierRequest tier;

    @Valid
    @NotNull(message = "Price Value data is required.")
    private PriceValueRequest value;
}