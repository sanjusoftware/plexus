package com.bankengine.pricing.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class PricingTierResponse {
    Long id;
    String name;
    String code;
    BigDecimal minThreshold;
    BigDecimal maxThreshold;
    List<TierConditionDto> conditions;
    List<ProductPricingCalculationResult.PriceComponentDetail> priceValues;
}