package com.bankengine.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO to bundle a PricingTier and its corresponding PriceValue
 * for a single request when creating tiered pricing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TierValueDto {
    @Valid
    @NotNull(message = "Pricing Tier data is required.")
    private PricingTierRequest tier;

    @Valid
    @NotNull(message = "Price Value data is required.")
    private PriceValueRequest value;
}