package com.bankengine.pricing.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class PricingTierResponse {
    private Long id;
    private String tierName;
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
    private List<TierConditionDto> conditions;
    private List<ProductPricingCalculationResult.PriceComponentDetail> priceValues;
}