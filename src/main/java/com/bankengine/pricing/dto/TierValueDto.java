package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO to bundle a PricingTier and its corresponding PriceValue
 * for a single request when creating tiered pricing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TierValueDto {
    @Valid // Apply validation to the nested DTO
    @NotNull(message = "Pricing Tier data is required.")
    private CreatePricingTierRequestDto tier;

    @Valid // Apply validation to the nested DTO
    @NotNull(message = "Price Value data is required.")
    private CreatePriceValueRequestDto value;
}