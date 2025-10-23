package com.bankengine.pricing.dto;

import com.bankengine.pricing.model.PricingTier;
import com.bankengine.pricing.model.PriceValue;
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
    private PricingTier tier;
    private PriceValue priceValue;
}